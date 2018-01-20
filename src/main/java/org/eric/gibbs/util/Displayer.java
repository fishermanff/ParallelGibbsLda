package org.eric.gibbs.util;

import java.util.*;

/**
 * Created by chenjianfeng on 2018/1/20.
 */

public class Displayer {
    public static String[] topicWordMat(double[][] phi, int words,
                                        HashMap<Integer, String> id2word){
        if(phi.length==0)
            return new String[0];
        int K = phi.length;
        int V = phi[0].length;
        String[] matrix = new String[K];
        for(int topic=0; topic<K; topic++){
            HashMap<Integer,Double> id2prob = new HashMap<Integer, Double>();
            for(int i=0; i<V; i++)
                id2prob.put(i, phi[topic][i]);
            List<HashMap.Entry<Integer,Double>> list =
                    new ArrayList<Map.Entry<Integer, Double>>(id2prob.entrySet());
            Collections.sort(list, new Comparator<Map.Entry<Integer,Double>>() {
                public int compare(Map.Entry<Integer,Double> o1, Map.Entry<Integer,Double> o2) {
                    if(o2.getValue()>o1.getValue())
                        return 1;
                    else if(o2.getValue()<o1.getValue())
                        return -1;
                    return 0;
                }
            });
            StringBuffer sb = new StringBuffer();
            sb.append("topic " + topic + " >>> ");
            for(int i=0; i<words; i++) {
                sb.append(id2word.get(list.get(i).getKey()) + ":" +
                        String.format("%.4f", list.get(i).getValue()));
                if(i<words-1)
                    sb.append(" ");
            }
            matrix[topic] = sb.toString();
        }
        return matrix;
    }
}
