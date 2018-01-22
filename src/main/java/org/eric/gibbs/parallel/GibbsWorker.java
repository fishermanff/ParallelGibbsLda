package org.eric.gibbs.parallel;

import java.util.concurrent.LinkedBlockingQueue;

/**
 * Created by chenjianfeng on 2018/1/21.
 */

public class GibbsWorker implements Runnable{
    private ParallelGibbsLda pGL;
    private int start;
    private int end;
    int[][] nw;
    int[] nwsum;
    LinkedBlockingQueue<Integer> blockingAllocate;
    LinkedBlockingQueue<Integer> blockingReduce;

    public GibbsWorker(ParallelGibbsLda pGL, int start, int end,
                       LinkedBlockingQueue<Integer> blockingAllocate,
                       LinkedBlockingQueue<Integer> blockingReduce){
        this.pGL = pGL;
        this.start = start;
        this.end = end;
        this.blockingAllocate = blockingAllocate;
        this.blockingReduce = blockingReduce;

        nw = new int[pGL.V][pGL.K];
        nwsum = new int[pGL.K];
    }

    public void run(){
        try {
            while(!Thread.interrupted())
                gibbsSampling();
        }catch (InterruptedException e){
            System.out.println(Thread.currentThread().getName() + " ends");
        }
    }

    public void gibbsSampling() throws InterruptedException{
        int task = 0;
        while (task!=1)
            task = blockingAllocate.take();

        // copy global nw, nwsum array
        for(int topic = 0; topic<pGL.K; topic++){
            nwsum[topic] = pGL.nwsum[topic];
            for(int word = 0; word< pGL.V; word++)
                nw[word][topic] = pGL.nw[word][topic];
        }

        for (int m=start; m<=end; m++) {
            int N = pGL.documents[m].length;
            for (int i=0; i<N; i++) {
                // remove z_i
                pGL.nd[m][pGL.z[m][i]]--;
                pGL.ndsum[m]--;
                nw[pGL.documents[m][i]][pGL.z[m][i]]--;
                nwsum[pGL.z[m][i]]--;

                // perform gibbs sampling formula and assign a topic to z[m][i]
                double[] prob = new double[pGL.K];
                for (int topic=0; topic<pGL.K; topic++) {
                    double p = (pGL.nd[m][topic]+pGL.alpha)/(pGL.ndsum[m]+ pGL.K*pGL.alpha) *
                            (nw[pGL.documents[m][i]][topic]+pGL.beta)/(nwsum[topic]+pGL.V*pGL.beta);
                    prob[topic] = p;
                }
                for (int idx=1; idx<pGL.K; idx++)
                    prob[idx] += prob[idx-1];
                double r = pGL.random.nextDouble()*prob[pGL.K-1];
                for (int topic=0; topic<pGL.K; topic++) {
                    if (r<prob[topic]) {
                        pGL.z[m][i] = topic;
                        break;
                    }
                }

                // update count variables
                pGL.nd[m][pGL.z[m][i]]++;
                pGL.ndsum[m]++;
                nw[pGL.documents[m][i]][pGL.z[m][i]]++;
                nwsum[pGL.z[m][i]]++;
            }
        }

        // emit a complete signal
        while(true){
            if(blockingAllocate.isEmpty()) {
                blockingReduce.put(1);
                break;
            }
        }
    }
}
