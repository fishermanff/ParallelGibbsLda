package org.eric.gibbs.util;

import java.util.HashMap;

/**
 * Created by chenjianfeng on 2018/1/20.
 */

public class DataBlock {
    public int[][] documents;
    public HashMap<String, Integer> word2id = new HashMap<String, Integer>();
    public HashMap<Integer, String> id2word = new HashMap<Integer, String>();
    public int V;
}
