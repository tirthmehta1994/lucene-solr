package org.apache.solr.search;

import java.io.IOException;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.util.TokenFilterFactory;
import org.apache.solr.analysis.ReversedWildcardFilterFactory;
import org.apache.solr.analysis.TokenizerChain;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.handler.component.ResponseBuilder;
import org.apache.solr.handler.component.SearchComponent;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.response.SolrQueryResponse;
import org.apache.solr.search.SortSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This search component can be plugged into your SearchHandler if you would like to block some well known expensive queries.
 * The queries that are blocked and failed by component currently are deep pagination queries as they are known to consume lot of memory and CPU
 * <ul>
 *  <li> queries with a start offset which is greater than the configured maxStartOffset config parameter value
 *  <li> queries with a row param value which is greater than the configured maxRowsFetch config parameter value
 * </ul>
 *
 * In future we would also like to extend this component to prevent
 * <ul>
 *  <li> facet pivot queries, controlled by a config param
 *  <li> regular facet queries, controlled by a config param
 *  <li> query with wildcard in the prefix if the field does not have ReversedWildCartPattern configured
 * </ul>
 *
 *
 */

public class BlockExpensiveQueries extends SearchComponent {

    private static final Logger LOG = LoggerFactory.getLogger(BlockExpensiveQueries.class);

    private int maxStartOffset = 10000;
    private int maxRowsFetch = 1000;
    private NamedList<?> initParams;

    @Override
    @SuppressWarnings("unchecked")
    public void init(NamedList args) {
        LOG.info("Loading the BlockExpensiveQueries component");
        super.init(args);
        this.initParams = args;

        if (args != null) {
            Object o = args.get("defaults");
            if (o != null && o instanceof NamedList) {
                maxStartOffset = (Integer)((NamedList)o).get("maxStartOffset");
                maxRowsFetch = (Integer)((NamedList)o).get("maxRowsFetch");
                LOG.info("Using maxStartOffset={}. maxRowsFetch={}", maxStartOffset, maxRowsFetch);
            }
        } else {
            LOG.info("Using default values, maxStartOffset={}. maxRowsFetch={}", maxStartOffset, maxRowsFetch);
        }
    }

    @Override
    public void prepare(ResponseBuilder rb) throws IOException {
        SolrQueryRequest req = rb.req;
        SolrQueryResponse rsp = rb.rsp;
        SortSpec sortSpec = rb.getSortSpec();
        int offset = sortSpec.getOffset();
        int count = sortSpec.getCount();
        LOG.info("Query offset={}, rows={}", offset, count);

        //check if cursorMark is used if we would like to allow deep pagination with cursor mark queries
        boolean isDistributed = req.getParams().getBool("distrib", true);
        if (isDistributed) {
            String cursorMarkMsg = "Queries with high \"start\" or high \"rows\" parameters are a performance problem in Solr. " +
                                   "If you really have a use-case for such queries, consider using \"cursors\" for pagination of results. " +
                                   "Refer: https://lucene.apache.org/solr/guide/pagination-of-results.html.";
            if (offset > maxStartOffset) {
                throw new IOException(String.format("The start=%s value exceeded the max offset allowed value of %s. %s",
                                          offset, maxStartOffset, cursorMarkMsg));
            }
            if (count > maxRowsFetch) {
                throw new IOException(String.format("The rows=%s value exceeded the max document fetch allowed value of %s. %s",
                                          count, maxRowsFetch, cursorMarkMsg));
            }
        }
    }

    @Override
    public void process(ResponseBuilder rb) throws IOException {

    }

    @Override
    public String getDescription() {
        return "Block Expensive Queries Component";
    }

}
