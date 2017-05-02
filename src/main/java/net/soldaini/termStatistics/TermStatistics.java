package net.soldaini.termStatistics;
import org.terrier.querying.Manager;
import org.terrier.structures.Index;
import org.terrier.querying.SearchRequest;
import org.terrier.matching.ResultSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.terrier.utility.ApplicationSetup;

import java.io.IOException;

public class TermStatistics {
    private Index index;
    protected static final Logger logger = LoggerFactory.getLogger(TermStatistics.class);

    public void close() {
        try {
            index.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

	public TermStatistics() {
        index = Index.createIndex();
    }

    public ResultSet GetSearchResults(){
        Manager queryingManager = new Manager(this.index);
        SearchRequest srq = queryingManager.newSearchRequestFromQuery("apple");
        srq.addMatchingModel("Matching","BM25");
        queryingManager.runSearchRequest(srq);
        return srq.getResultSet();
    }

    public static void main(String[] args) {
        TermStatistics ts = new TermStatistics();
        ResultSet results = ts.GetSearchResults();

        int [] documentIds = results.getDocids();
        double [] documentScores = results.getScores();


        return;
    }
}
