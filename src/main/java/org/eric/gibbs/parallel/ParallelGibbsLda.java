package org.eric.gibbs.parallel;

import jdk.nashorn.internal.runtime.regexp.joni.exception.ValueException;
import org.eric.gibbs.serial.SerialGibbsLda;
import org.eric.gibbs.util.DataBlock;
import org.eric.gibbs.util.Displayer;
import org.eric.gibbs.util.TextLoader;

import java.util.Random;
import java.util.concurrent.*;

/**
 * Created by chenjianfeng on 2018/1/17.
 */

public class ParallelGibbsLda {
    /**
     * document-word matrix
     */
    int[][] documents;

    /**
     * vocabulary size
     */
    int V;

    /**
     * number of topics
     */
    int K;

    /**
     * document->topic Dirichlet alpha
     */
    double alpha;

    /**
     * topic->word Dirichlet beta
     */
    double beta;

    /**
     * max iteration
     */
    int iter;

    /**
     * initial with seed for result reproduction
     */
    Random random;

    /**
     * nd[m][k]: number of words in document m assigned to topic k; size M * K
     */
    int[][] nd;

    /**
     * ndsum[m]: total number of words in document m; size M
     */
    int[] ndsum;

    /**
     * nw[i][k]: number of instances of word i assigned to topic k; size V * K
     */
    int[][] nw;

    /**
     * nwsum[k]: total number of words assigned to topic k; size K
     */
    int[] nwsum;

    /**
     * z[m][i]: the assigned topic of word i in document m
     */
    int[][] z;

    /**
     * store estimated theta matrix
     */
    private double[][] theta;

    /**
     * store estimated phi matrix
     */
    private double[][] phi;

    /**
     * store perplexity
     */
    private double perplexity = -1;

    LinkedBlockingQueue<Integer> blockingAllocate = new LinkedBlockingQueue<Integer>();
    LinkedBlockingQueue<Integer> blockingReduce = new LinkedBlockingQueue<Integer>();

    public ParallelGibbsLda(int[][] documents, int V){
        this.documents = documents;
        this.V = V;
    }

    private void initial(){
        int M = documents.length;
        z = new int[M][];
        nd = new int[M][K];
        ndsum = new int[M];
        nw = new int[V][K];
        nwsum = new int[K];

        for(int m=0; m<M; m++){
            int N = documents[m].length;
            z[m] = new int[N];
            for(int i=0; i<N; i++){
                int topic = random.nextInt(K);
                z[m][i] = topic;
                nd[m][topic]++;
                nw[documents[m][i]][topic]++;
                nwsum[topic]++;
            }
            ndsum[m] = N;
        }
    }

    public void gibbsSampling(int K, double alpha, double beta, int iter,
                                int threads){
        gibbsSampling(K, alpha, beta, iter, threads, 47);
    }

    public void gibbsSampling(int K, double alpha, double beta, int iter,
                                int threads, int seed){
        if(threads<=0)
            throw new ValueException("threads should be more than zero");
        this.K = K;
        this.alpha = alpha;
        this.beta = beta;
        this.iter = iter;
        this.random = new Random(seed);

        initial();
        GibbsWorker[] gibbsWorks = new GibbsWorker[threads];
        ExecutorService executor = Executors.newCachedThreadPool();
        int pieceSize = documents.length/threads;
        for(int i=0, offset=0; i<threads; i++) {
            if(i==threads-1)
                pieceSize = documents.length - offset;
            System.out.println("Thread " + i + ", start: " + offset + ", end: " + (offset+pieceSize-1));
            gibbsWorks[i] = new GibbsWorker(this, offset, offset+pieceSize-1,
                                                blockingAllocate, blockingReduce);
            offset += pieceSize;
            executor.execute(gibbsWorks[i]);
        }
        int it = 0;
        try {
            while((it++<iter)) {
                // starting up threads
                for(int i=0; i<threads; i++)
                    blockingAllocate.put(1);

                int taskFinished = 0;
                while (taskFinished < threads) {
                    taskFinished += blockingReduce.take();
                }

                // reduce result of each thread and update global nw, nwsum array
                for(int topic=0; topic<K; topic++){
                    int wordCount = 0;
                    for(int word=0; word<V; word++){
                        int nwDelta = 0;
                        for(int i=0; i<threads; i++){
                            nwDelta += (gibbsWorks[i].nw[word][topic] - nw[word][topic]);
                        }
                        nw[word][topic] += nwDelta;
                        wordCount += nw[word][topic];
                    }
                    nwsum[topic] = wordCount;
                }

                if(it%10 == 0)
                    System.out.println("gibbs iterating [" + it + "/" + iter + "] ...");
            }
        }catch(InterruptedException e){
            e.printStackTrace();
        }
        System.out.println("Gibbs sampling off");
        executor.shutdownNow();
        // store theta and phi matrix after estimation
        theta = calcThetaMatrix();
        phi = calcPhiMatrix();
    }

    private double[][] calcThetaMatrix(){
        int M =  documents.length;
        double[][] theta = new double[M][K];
        for(int m=0; m<M; m++){
            for(int topic=0; topic<K; topic++){
                theta[m][topic] = (nd[m][topic]+alpha)/(ndsum[m]+K*alpha);
            }
        }
        return theta;
    }

    private double[][] calcPhiMatrix(){
        int M = documents.length;
        double[][] phi = new double[K][V];
        for(int w=0; w<V; w++){
            for(int topic=0; topic<K; topic++)
                phi[topic][w] = (nw[w][topic]+beta)/(nwsum[topic]+V*beta);
        }
        return phi;
    }

    public double perplexity(){
        if(perplexity!=-1){
            return perplexity;
        }
        double num = 0;
        double den = 0;
        int M = documents.length;
        for(int m=0; m<M; m++){
            int N = documents[m].length;
            for(int i=0; i<N; i++){
                int topic = z[m][i];
                num -= Math.log(theta[m][topic]*phi[topic][documents[m][i]]);
            }
            den += N;
        }
        perplexity = Math.exp(num/den);
        return perplexity;
    }

    public double[] infer(int[] document){
        // todo
        double[] theta = new double[document.length];
        return theta;
    }

    public static void main(String[] args) throws Exception{
        int M = 2000;
        int K = 30;
        DataBlock data = TextLoader.loadData("news.txt", M, "vocab_2w.txt");
        System.out.println("vocab size " + data.V);
        ParallelGibbsLda gibbsLda = new ParallelGibbsLda(data.documents, data.V);
        long t0 = System.currentTimeMillis();
        gibbsLda.gibbsSampling(K,2,0.5,100, 5);
        System.out.println("training process cost " +
                (System.currentTimeMillis()-t0)/1000 + "s");
        System.out.println("after training, the corpus perplexity is " +
                String.format("%.4f", gibbsLda.perplexity()));
        String[] topicWords = Displayer.topicWordMat(gibbsLda.phi, 10, data.id2word);
        for(String str: topicWords)
            System.out.println(str);
    }
}
