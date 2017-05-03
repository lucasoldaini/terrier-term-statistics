package net.soldaini.TermStatistics;

import org.json.JSONArray;
import org.json.JSONObject;
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
    private PostingIndex <Pointer> directIndex;
    private DocumentIndex documentIndex;
    private MetaIndex metaIndex;
    private Lexicon<String> lex;
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
        directIndex = (PostingIndex<Pointer>) index.getDirectIndex();
        invertedIndex = (PostingIndex<Pointer>) index.getInvertedIndex();
        documentIndex = index.getDocumentIndex();
        metaIndex = index.getMetaIndex();
        lex = index.getLexicon();
    }

    private SearchRequest getSearchResults(String queryString){
        Manager queryingManager = new Manager(this.index);
        SearchRequest srq = queryingManager.newSearchRequestFromQuery(queryString);
        srq.addMatchingModel("Matching","BM25");
        srq.setControl("decorate", "on");
        queryingManager.runSearchRequest(srq);
        return srq;
    }

    private double [][] getTermsTf(String [] queryTerms, int [] docIds) throws IOException {
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

    private double [] getTermsDf(String [] queryTerms) throws IOException {
        double[] dfs = new double[queryTerms.length];

        for (int i = 0; i < queryTerms.length; i++){
            LexiconEntry le = this.lex.getLexiconEntry(queryTerms[i]);
            if (le != null){
                dfs[i] = le.getDocumentFrequency();
            }
        }
        return dfs;
    }

    private double [] getDocsLength(int [] docIds) throws IOException {
        double[] docLengths = new double[docIds.length];

        for (int j = 0; j < docIds.length; j++){
            docLengths[j] = this.documentIndex.getDocumentLength(docIds[j]);
        }

        return docLengths;
    }

    private String normalizeString(String str){
        String normed = str.toLowerCase();
        normed = normed.replaceAll("([\\p{P}\\s]+$|^[\\p{P}\\s]+)", "");
        normed = normed.split("\\^")[0];
        return normed;
    }


    public JSONObject getQueryStatistics(String queryString) throws IOException {
        String [] queryTerms  = queryString.split("\\s+");

        // we keep the tokenized origninal query here
        List <String> mutableTokenizedOrigQuery = new LinkedList<>();

        SearchRequest request = this.getSearchResults(queryString);

        // Get terms of query that have been used for searching;
        // note that some terms might have been stemmed while stearching
        Query query = request.getQuery();
        List <String> mutableModQueryTerms = new LinkedList<>(
                Arrays.asList(query.toString().split(" ")));

        // We align the (possibly stemmed or removed) terms with the
        // original query
        int i = 0;
        String mutedTerm, origTerm;
        while (i < queryTerms.length) {

            if (i < mutableModQueryTerms.size()) {
                mutedTerm = normalizeString(mutableModQueryTerms.get(i));
            } else {
                mutedTerm = "";
            }

            origTerm = normalizeString(queryTerms[i]);

            while (! origTerm.startsWith(mutedTerm) && i < queryTerms.length){
                mutableTokenizedOrigQuery.add(origTerm);
                mutableModQueryTerms.add(i, "");

                i++;
                origTerm = normalizeString(queryTerms[i]);

            }
            mutableTokenizedOrigQuery.add(origTerm);

            if (i >= mutableModQueryTerms.size()) {
                mutableModQueryTerms.add(mutedTerm);
            } else{
                mutableModQueryTerms.set(i, mutedTerm);
            }

            i++;
        }

        // finally, we turn the terms into an array
        String [] modQueryTerms = mutableModQueryTerms.toArray(
                new String [mutableModQueryTerms.size()]);
        String [] tokenizedOrigQuery = mutableTokenizedOrigQuery.toArray(
                new String [mutableTokenizedOrigQuery.size()]);

        // Get id and scores of results.
        ResultSet results = request.getResultSet();
        int [] documentIds = results.getDocids();
        double [] documentScores = results.getScores();

        // Get statistcs necessary for scoring
        double [][] tfs = this.getTermsTf(modQueryTerms, documentIds);
        double [] dfs = this.getTermsDf(modQueryTerms);
        double [] docsLengths = this.getDocsLength(documentIds);
        double avgDocsLengths = this.index.getCollectionStatistics().getAverageDocumentLength();

        JSONObject jsonOut = new JSONObject();
        jsonOut.put("tfs", tfs);
        jsonOut.put("dfs", dfs);
        jsonOut.put("doc_len", docsLengths);
        jsonOut.put("avg_doc_len", avgDocsLengths);
        jsonOut.put("avg_doc_len", avgDocsLengths);
        jsonOut.put("query", queryString);
        jsonOut.put("terms", tokenizedOrigQuery);

        return jsonOut;

    }

    public static void main(String[] args) throws IOException {
        TermStatistics ts = new TermStatistics();
        ts.getQueryStatistics("an Apple^2.0 pie; a treat! the");

        ts.close();
    }
}
