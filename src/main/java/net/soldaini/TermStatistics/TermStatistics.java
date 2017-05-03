package net.soldaini.TermStatistics;
import org.apache.commons.lang.ArrayUtils;
import org.terrier.querying.Manager;
import org.terrier.querying.parser.Query;
import org.terrier.structures.*;
import org.terrier.querying.SearchRequest;
import org.terrier.matching.ResultSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.terrier.structures.postings.IterablePosting;
import org.terrier.utility.ApplicationSetup;


import java.lang.String;
import java.io.IOException;
import java.util.*;

public class TermStatistics {
    private Index index;
    private PostingIndex <Pointer> invertedIndex;
    private MetaIndex metaIndex;
    Lexicon<String> lex;
    protected static final Logger logger = LoggerFactory.getLogger(TermStatistics.class);
//    private ApplicationSetup properties;

    public void close() {
        try {
            index.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

	public TermStatistics() {
        index = Index.createIndex();
        invertedIndex = (PostingIndex<Pointer>) index.getInvertedIndex();
        metaIndex = index.getMetaIndex();
        lex = index.getLexicon();
    }

    public SearchRequest getSearchResults(String [] queryTerms){
        @SuppressWarnings("Since15") String query = String.join(" ", queryTerms);

        Manager queryingManager = new Manager(this.index);
        SearchRequest srq = queryingManager.newSearchRequestFromQuery(query);
        srq.addMatchingModel("Matching","BM25");
        srq.setControl("decorate", "on");
        queryingManager.runSearchRequest(srq);
        return srq;
    }

    public double [] getTermsTf(String [] queryTerms, int [] docIds) throws IOException {
        List <double []> tfs;

        HashSet <Integer> docIdsSet = new HashSet<Integer>(
                Arrays.asList(ArrayUtils.toObject(docIds)));

         for (int i = 0; i < queryTerms.length; i++){
             i++;
             LexiconEntry le = this.lex.getLexiconEntry(queryTerms[i]);
             IterablePosting postings = this.invertedIndex.getPostings(le);
             for (int j = 0; j < docIds.length; j ++){
                 while (postings.next() != IterablePosting.EOL) {
                     Map.Entry<String,LexiconEntry> lee = lex.getLexiconEntry(postings.getId());
                     System.out.print(lee.getKey() + " with frequency " + postings.getFrequency());
}
            }
         }


        double a[]= new double[0];
        return a;
    }

    public static void main(String[] args) throws IOException {
        String [] queryTerms = {"an", "Apple^2.0", "pie"};

        TermStatistics ts = new TermStatistics();
        SearchRequest request = ts.getSearchResults(queryTerms);

        // Get terms of query; note that some terms might have been
        // stemmed while stearching; we do out best to align them.
        Query query = request.getQuery();
        List <String> mutableModQueryTerms = new LinkedList<String>(
                Arrays.asList(query.toString().split(" ")));


        int i = 0;
        while (i < queryTerms.length){
            String term = mutableModQueryTerms.get(i).split("\\^")[0];
            while (!queryTerms[i].toLowerCase().startsWith(term)){
                mutableModQueryTerms.add(i, "");
                i++;
            }
            mutableModQueryTerms.set(i, term);
            i++;
        }

        String [] modQueryTerms = mutableModQueryTerms.toArray(
                new String [mutableModQueryTerms.size()]);

        // Get id and scores of results.
        ResultSet results = request.getResultSet();
        int [] documentIds = results.getDocids();
        double [] documentScores = results.getScores();

        double tfs[] = ts.getTermsTf(modQueryTerms, documentIds);
    }
}
