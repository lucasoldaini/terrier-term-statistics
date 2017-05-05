package net.soldaini.termstatistics;

import java.io.*;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;


import org.slf4j.LoggerFactory;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import org.json.JSONArray;
import org.tartarus.snowball.ext.PorterStemmer;
import org.w3c.dom.Document;
import org.json.JSONObject;
import org.terrier.querying.Manager;
import org.terrier.structures.*;
import org.terrier.querying.SearchRequest;
import org.terrier.structures.postings.IterablePosting;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import org.w3c.dom.Element;

import java.lang.String;
import java.util.*;



public class TermStatistics {
    private Index index;
    private PostingIndex <Pointer> invertedIndex;
    private DocumentIndex documentIndex;
    private MetaIndex metaIndex;
    private Lexicon<String> lex;
    private org.terrier.terms.PorterStemmer stemmer;

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
        documentIndex = index.getDocumentIndex();
        metaIndex = index.getMetaIndex();
        lex = index.getLexicon();
        stemmer = new org.terrier.terms.PorterStemmer();
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

    private String stem(String term){
//        this.stemmer.setCurrent(term);
//        stemmer.stem();
//        return stemmer.getCurrent();
        return this.stemmer.stem(term);
    }

    private String [] getDocNames (int [] docIds) throws IOException {
        String [] docNames = new String[docIds.length];
        for (int i = 0; i < docIds.length; i++) {
            docNames[i] = this.getDocNames(docIds[i]);
        }
        return docNames;
    }

    private String  getDocNames (int docId) throws IOException {
        return this.metaIndex.getAllItems(docId)[0];
    }

    private int [] getDocIds (String [] docNames) throws IOException {
        int [] docIds = new int[docNames.length];
        for (int i = 0; i < docIds.length; i++) {
            docIds[i] = this.getDocIds(docNames[i]);
        }
        return docIds;
    }

    private int getDocIds (String docName) throws IOException {
        return this.metaIndex.getDocument("docno", docName);
    }

    private JSONObject getQueryStatistics(String queryString, int queryId, HashSet <Integer> queryDocIdsSet) throws IOException {
        String [] queryTerms  = queryString.split("[\\P{L}\\s]+");
        SearchRequest request = this.getSearchResults(String.join(" ", queryTerms));

        List <String> mutSubmittedTerms= new LinkedList<>(
                Arrays.asList(request.getQuery().toString().split(" ")));

        int i = 0;
        while (i < mutSubmittedTerms.size()) {
            String subTerm = mutSubmittedTerms.get(i);
            String normedSubTerm = normalizeString(subTerm);
            String normedQueryTerm = stem(normalizeString(queryTerms[i]));

            if (!normedQueryTerm.equals(normedSubTerm)) {
                mutSubmittedTerms.add(i, "");
            }
            i++;
        }

        String [] subittedTerms = mutSubmittedTerms.toArray(
                new String [mutSubmittedTerms.size()]);

        int [] documentIds = new int[queryDocIdsSet.size()];
        int h = 0;
        for (int docId : queryDocIdsSet) {
            documentIds[h] = docId; h += 1;
        }
        Arrays.sort(documentIds);

        // Get statistcs necessary for scoring
        double [][] tfs = this.getTermsTf(subittedTerms, documentIds);
        double [] dfs = this.getTermsDf(subittedTerms);
        double [] docsLengths = this.getDocsLength(documentIds);
        double avgDocsLengths = this.index.getCollectionStatistics().getAverageDocumentLength();

        JSONObject jsonOut = new JSONObject();
        jsonOut.put("tfs", tfs);
        jsonOut.put("dfs", dfs);
        jsonOut.put("doc_len", docsLengths);
        jsonOut.put("avg_doc_len", avgDocsLengths);
        jsonOut.put("query", queryString);
        jsonOut.put("terms", queryTerms);
        jsonOut.put("doc_ids", this.getDocNames(documentIds));
        jsonOut.put("qid", queryId);
        return jsonOut;

    }

    private HashMap <Integer, String> loadQueries(String queryPath) throws IOException, SAXException, ParserConfigurationException {
        File file = new File(queryPath);
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        DocumentBuilder db = dbf.newDocumentBuilder();
        Document topics = db.parse(file);
        topics.getDocumentElement().normalize();

        HashMap <Integer, String> queriesMap = new HashMap<>();
        NodeList queries = topics.getElementsByTagName("query");
        for (int i = 0; i < queries.getLength(); i++) {
            Element query = (Element) queries.item(i);
            int queryId = Integer.parseInt(query.getElementsByTagName("id").item(0).getTextContent().trim());
            String queryText = query.getElementsByTagName("title").item(0).getTextContent();

            queriesMap.put(queryId, queryText);
        }

        return queriesMap;
    }

    private HashMap <Integer, HashSet <Integer>> loadDocuments(String path) throws IOException{
        HashMap <Integer, HashSet <Integer>> documentsIdsMap = new HashMap<>();

        BufferedReader br = new BufferedReader(new FileReader(path));
        String line = null;
        while ((line = br.readLine()) != null) {
            String [] parsed = line.split("\\s");
            Integer queryId = Integer.parseInt(parsed[0]);
            String docName = parsed[1].trim();

            if (! documentsIdsMap.containsKey(queryId)) {
                documentsIdsMap.put(queryId, new HashSet<>());
            }
            int docId = this.getDocIds(docName);
            if (docId >= 0){documentsIdsMap.get(queryId).add(docId);}
        }

        return documentsIdsMap;
    }

    public static void main(String[] args) throws IOException, SAXException, ParserConfigurationException  {
        // raise the level of the logger
        Logger root = (Logger)LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        root.setLevel(Level.WARN);

        // parse from input the path to the
        String queriesPath = "";
        String docIdsPath = "";
        String outputPath = "";
        try {
            queriesPath = args[0];
            docIdsPath = args[1];
            outputPath = args[2];

        } catch (ArrayIndexOutOfBoundsException e) {
            System.err.println("USAGE: java -jar TermStatistics.jar [queriesPath] [docIdsPath] [outputPath]");
            System.exit(1);
        }

        TermStatistics ts = new TermStatistics();
        System.out.println("[info] loaded index.");

        HashMap <Integer, String> queriesMap = ts.loadQueries(queriesPath);
        HashMap <Integer, HashSet <Integer>> docIdsMap = ts.loadDocuments(docIdsPath);
        System.out.println("[info] queries and documents loaded.");

        JSONArray queriesJson = new JSONArray();

        Iterator it = queriesMap.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry pair = (Map.Entry) it.next();

            HashSet <Integer> queryDocIdsSet = docIdsMap.get((int) pair.getKey());
            JSONObject queryJson =ts.getQueryStatistics(
                    (String) pair.getValue(), (int) pair.getKey(), queryDocIdsSet);
            queriesJson.put(queryJson);
            it.remove(); // avoids a ConcurrentModificationException
        }

        try{
            PrintWriter writer = new PrintWriter(outputPath, "UTF-8");
            writer.println(queriesJson.toString());
            writer.close();
        } catch (IOException e) {}
        System.out.println("[info] data written to destination.");

        ts.close();
    }
}
