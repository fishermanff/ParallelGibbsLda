package org.eric.gibbs.util;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;

/**
 * Created by chenjianfeng on 2018/1/20.
 */

public class TextLoader {
    public static DataBlock loadData(String filename, int M) throws Exception{
        return loadData(filename, M, "");
    }

    public static DataBlock loadData(String filename, int M, String vocabname) throws Exception{
        DataBlock dataBlock = new DataBlock();
        if(!vocabname.equals("")) {
            InputStream in = TextLoader.class.getClassLoader().getResourceAsStream(vocabname);
            BufferedReader br = new BufferedReader(new InputStreamReader(in));
            String line = "";
            while((line=br.readLine())!=null){
                String[] wi = line.split(" ");
                dataBlock.word2id.put(wi[0], Integer.parseInt(wi[1]));
                dataBlock.id2word.put(Integer.parseInt(wi[1]), wi[0]);
            }
        }

        dataBlock.documents = new int[M][];
        InputStream in = TextLoader.class.getClassLoader().getResourceAsStream(filename);
        BufferedReader br = new BufferedReader(new InputStreamReader(in));
        int vid = 0;
        for(int m=0; m<M; m++){
            String line = br.readLine();
            String[] lineSplit = line.split("\t");
            String[] words = lineSplit[1].split(" ");
            int N = words.length;
            dataBlock.documents[m] = new int[N];
            for(int i=0; i<N; i++) {
                if(vocabname.equals("") && !dataBlock.word2id.containsKey(words[i])){
                    dataBlock.word2id.put(words[i], vid);
                    dataBlock.id2word.put(vid, words[i]);
                    vid++;
                }
                // if use pre-defined vocab, skip words not in vocab
                if(dataBlock.word2id.containsKey(words[i]))
                    dataBlock.documents[m][i] = dataBlock.word2id.get(words[i]);
            }
        }
        dataBlock.V = (vid==0? dataBlock.word2id.size():vid);
        return dataBlock;
    }

    public static void main(String[] args) throws Exception{
        TextLoader textLoader = new TextLoader();
        DataBlock db = textLoader.loadData("news.txt", 5000);
        System.out.println("documents size: " + db.documents.length);
        System.out.println("word2id size: " + db.word2id.size());
        System.out.println("id2word size: " + db.id2word.size());
        System.out.println("V: " + db.V);
    }
}
