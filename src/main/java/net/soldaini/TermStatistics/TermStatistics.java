package net.soldaini.TermStatistics;
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
import java.util.Arrays;
import java.util.List;

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

    public double [] getTermsTf(String [] queryTerms, int [] docIds) throws IOException {
        List <double []> tfs;
//        BitIndexPointer[] termIds = new BitIndexPointer[queryTerms.length];

        String term;
        for (int i = 0; i < queryTerms.length; i++) {
            term = queryTerms[i].split("\\^")[0];
            LexiconEntry le = this.lex.getLexiconEntry(term);
            IterablePosting postings = this.invertedIndex.getPostings(le);


            i = i + 1 - 1;
        }

         for (int i = 0; i < queryTerms.length; i++){
            for (int j = 0; j < docIds.length; j ++){

            }
         }


        double a[]= new double[0];
        return a;
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

    public static void main(String[] args) throws IOException {
        String [] queryTerms = {"an", "Apple^2.0", "pie"};

        TermStatistics ts = new TermStatistics();
        SearchRequest request = ts.getSearchResults(queryTerms);

        // Get terms of query; note that some terms might have been
        // stemmed while stearching; we do out best to align them.
        Query query = request.getQuery();
        String [] modQueryTerms = query.toString().split(" ");
        int i = 0;
        while (i < queryTerms.length){
            term = queryTerms[i].split("\\^")[0];
            i++;
        }


        // Get id and scores of results.
        ResultSet results = request.getResultSet();
        int [] documentIds = results.getDocids();
        double [] documentScores = results.getScores();

        double tfs[] = ts.getTermsTf(queryTerms, documentIds);
    }
}
