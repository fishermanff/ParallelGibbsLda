package org.eric.gibbs.serial;

import java.util.Random;
import org.eric.gibbs.util.*;

/**
 * Created by chenjianfeng on 2017/7/17.
 */

public class SerialGibbsLda {
    /**
     * document-word matrix
     */
    private int[][] documents;

    /**
     * vocabulary size
     */
    private int V;

    /**
     * number of topics
     */
    private int K;

    /**
     * document->topic Dirichlet alpha
     */
    private double alpha;

    /**
     * topic->word Dirichlet beta
     */
    private double beta;

    /**
     * max iteration
     */
    private int iter;

    /**
     * initial with seed for result reproduction
     */
    private Random random;

    /**
     * nd[m][k]: number of words in document m assigned to topic k; size M * K
     */
    private int[][] nd;

    /**
     * ndsum[m]: total number of words in document m; size M
     */
    private int[] ndsum;

    /**
     * nw[i][k]: number of instances of word i assigned to topic k; size V * K
     */
    private int[][] nw;

    /**
     * nwsum[k]: total number of words assigned to topic k; size K
     */
    private int[] nwsum;

    /**
     * z[m][i]: the assigned topic of word i in document m
     */
    private int[][] z;

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

    public SerialGibbsLda(int[][] documents, int V){
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

    public void gibbsSampling(int K, double alpha, double beta, int iter){
        gibbsSampling(K, alpha, beta, iter, 47);
    }

    public void gibbsSampling(int K, double alpha, double beta, int iter, int seed){
        this.K = K;
        this.alpha = alpha;
        this.beta = beta;
        this.iter = iter;
        this.random = new Random(seed);

        int it = 0;
        initial();
        while(it++<iter){
            int M = documents.length;
            for(int m=0; m<M; m++){
                int N = documents[m].length;
                for(int i=0; i<N; i++){
                    // remove z_i
                    nd[m][z[m][i]]--;
                    ndsum[m]--;
                    nw[documents[m][i]][z[m][i]]--;
                    nwsum[z[m][i]]--;

                    // perform gibbs sampling formula and assign a topic to z[m][i]
                    double[] prob = new double[K];
                    for(int topic=0; topic<K; topic++){
                        double p = (nd[m][topic]+alpha)/(ndsum[m]+K*alpha) *
                                (nw[documents[m][i]][topic]+beta)/(nwsum[topic]+V*beta);
                        prob[topic] = p;
                    }
                    for(int idx=1; idx<K; idx++)
                        prob[idx] += prob[idx-1];
                    double r = random.nextDouble() * prob[K-1];
                    for(int topic=0; topic<K; topic++){
                        if(r<prob[topic]) {
                            z[m][i] = topic;
                            break;
                        }
                    }

                    // update count variables
                    nd[m][z[m][i]]++;
                    ndsum[m]++;
                    nw[documents[m][i]][z[m][i]]++;
                    nwsum[z[m][i]]++;
                }
            }
            if(it%10 == 0)
                System.out.println("gibbs iterating [" + it +
                                        "/" + iter + "] ...");
        }

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
        SerialGibbsLda gibbsLda = new SerialGibbsLda(data.documents, data.V);
        long t0 = System.currentTimeMillis();
        gibbsLda.gibbsSampling(K,2,0.5,200);
        System.out.println("training process cost " +
                (System.currentTimeMillis()-t0)/1000 + "s");
        System.out.println("after training, the corpus perplexity is " +
                String.format("%.4f", gibbsLda.perplexity()));
        String[] topicWords = Displayer.topicWordMat(gibbsLda.phi, 10, data.id2word);
        for(String str: topicWords)
            System.out.println(str);
    }
}
