package net.soldaini.TermStatistics;

import org.terrier.querying.Manager;
import org.terrier.querying.parser.Query;
import org.terrier.structures.*;
import org.terrier.querying.SearchRequest;
import org.terrier.matching.ResultSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.terrier.structures.postings.IterablePosting;



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

    public double [][] getTermsTf(String [] queryTerms, int [] docIds) throws IOException {
        double[][] tfs = new double[queryTerms.length][docIds.length];

        HashMap <Integer, Integer> docIdsMap = new HashMap <>();
        for (int j = 0; j < docIds.length; j++) {
            docIdsMap.put(docIds[j], j);
        }

        int currentDocId;
         for (int i = 0; i < queryTerms.length; i++){

             LexiconEntry le = this.lex.getLexiconEntry(queryTerms[i]);
             if (le == null) continue;

             IterablePosting postings = this.invertedIndex.getPostings(le);
             while (postings.next() != IterablePosting.EOL) {
                 currentDocId = postings.getId();
                 if (docIdsMap.containsKey(currentDocId)) {
                     tfs[i][docIdsMap.get(currentDocId)] = postings.getFrequency();
                 }
             }
         }
        return tfs;
    }

    public static void main(String[] args) throws IOException {
        String [] queryTerms = {"an", "Apple^2.0", "pie"};

        TermStatistics ts = new TermStatistics();
        SearchRequest request = ts.getSearchResults(queryTerms);

        // Get terms of query; note that some terms might have been
        // stemmed while stearching; we do out best to align them.
        Query query = request.getQuery();
        List <String> mutableModQueryTerms = new LinkedList<>(
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

        double [][] tfs = ts.getTermsTf(modQueryTerms, documentIds);
    }
}
