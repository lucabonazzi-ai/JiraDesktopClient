package com.almworks.jira.provider3.sync.download2.details;

import com.almworks.api.connector.ConnectorException;
import com.almworks.integers.IntArray;
import com.almworks.jira.provider3.sync.ServerFields;
import com.almworks.jira.provider3.sync.download2.process.util.ProgressInfo;
import com.almworks.jira.provider3.sync.download2.rest.JqlSearch;
import com.almworks.restconnector.RestResponse;
import com.almworks.restconnector.RestSession;
import com.almworks.restconnector.jql.JqlQuery;
import com.almworks.restconnector.json.sax.CompositeHandler;
import com.almworks.restconnector.json.sax.JSONCollector;
import com.almworks.restconnector.json.sax.LocationHandler;
import com.almworks.restconnector.json.sax.PeekArrayElement;
import com.almworks.util.LogHelper;
import com.almworks.util.i18n.text.LocalizedAccessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.simple.parser.ParseException;

import java.io.IOException;

/**
 * Reads an issue query page by page from {@code /rest/api/3/search/jql}.
 * <p/>
 * That endpoint pages with a cursor: each response carries a {@code nextPageToken}
 * to be sent back for the following page, and the query is finished when no token
 * comes back. It reports no {@code total}, so the number of matching issues is not
 * known until the query has been read to the end. Callers that need a count can
 * only use {@link #getLoadedCount()}, which counts what has been read so far.
 */
public class RestQueryPager {
  @NotNull
  private final JqlQuery myJql;
  /**
   * HTTP response status codes that are treated as end of query. Otherwise failure exception is thrown.
   */
  private final IntArray myNoResultCodes = new IntArray();
  private String[] myFields = null;
  private int myMaxResult = -1;

  /** Cursor for the next page. Null before the first request. */
  private String myNextPageToken = null;
  /** Set once the server stopped offering a next page. */
  private boolean myLastPageLoaded = false;
  /** Issues read so far, across all pages. */
  private int myLoadedCount = 0;

  public RestQueryPager(@NotNull JqlQuery jql) {
    myJql = jql;
  }

  public static RestQueryPager allFields(@NotNull JqlQuery jql) {
    RestQueryPager pager = new RestQueryPager(jql);
    pager.setFields(new String[]{"*all"});
    return pager;
  }

  /**
   * If not null specified used to set fields via {@link JqlSearch#addFields(String...)}
   */
  public void setFields(@Nullable String[] fields) {
    myFields = fields;
  }

  public void setFields(ServerFields.Field ... fields) {
    String[] strIds = new String[fields.length];
    for (int i = 0; i < fields.length; i++) strIds[i] = fields[i].getJiraId();
    setFields(strIds);
  }

  private JqlSearch createSearch() {
    JqlSearch search = new JqlSearch(myJql);
    if (myFields != null) search.addFields(myFields);
    return search;
  }

  /**
   * @return number of issues read so far. Unlike the total count the previous
   * endpoint reported, this only becomes the size of the result set once
   * {@link #isLastPageLoaded()} is true.
   */
  public int getLoadedCount() {
    return myLoadedCount;
  }

  /**
   * @return true when the server has stopped offering a next page, so the query has
   * been read to the end
   */
  public boolean isLastPageLoaded() {
    return myLastPageLoaded;
  }

  /**
   * @see #myNoResultCodes
   */
  public void addNoResultCode(int httpCode) {
    myNoResultCodes.add(httpCode);
  }

  /**
   * Sets desired max query result. It should no be too big because of it may lead to JIRA failure.
   * @param maxResult
   */
  public void setMaxResult(int maxResult) {
    myMaxResult = maxResult;
  }

  /**
   * Reads the next page and hands its issues to the handler.
   *
   * @param issueHandler handler that consumes issues (elements of "issues" array)
   * @return number of issues read from this page. Zero means nothing was read,
   * which happens on the "no result" status codes and on an empty last page.
   * Callers must not use the return value to compute a position: the next page is
   * requested with the cursor kept by this object, and {@link #isLastPageLoaded()}
   * is what says whether another page is worth asking for.
   */
  public int loadNext(RestSession session, LocationHandler issueHandler) throws ConnectorException {
    if (myLastPageLoaded) return 0;
    JqlSearch search = createSearch();
    if (myMaxResult < 0) search.setDefaultMaxResult();
    else search.setMaxResult(myMaxResult);
    search.setNextPageToken(myNextPageToken);
    RestResponse response = search.request(session);
    if (!response.isSuccessful()) {
      int statusCode = response.getStatusCode();
      if (myNoResultCodes.contains(statusCode)) {
        myLastPageLoaded = true; // Set query ended state
        return 0;
      }
      RestResponse.ErrorResponse errorResponse = response.createErrorResponse();
      if (statusCode == 400) {
        ConnectorException problem = search.maybeInaccessibleProject(session, errorResponse);
        if (problem != null) throw problem;
      }
      LogHelper.warning("Query failed", statusCode, response.getLastUrl(), search);
      throw errorResponse.toException();
    }
    JSONCollector getNextPageToken = new JSONCollector(null);
    JSONCollector getIsLast = new JSONCollector(null);
    CountingHandler counter = new CountingHandler(issueHandler);
    response.parseJSON(new CompositeHandler(
      getNextPageToken.peekObjectEntry("nextPageToken"),
      getIsLast.peekObjectEntry("isLast"),
      PeekArrayElement.entryArray("issues", counter)
    ));
    myNextPageToken = getNextPageToken.getString();
    // The cursor is the signal that matters: the endpoint documentation notes that
    // isLast is not returned by every operation. Honour it when it is there.
    if (myNextPageToken == null || myNextPageToken.isEmpty() || Boolean.TRUE.equals(getIsLast.getObject())) {
      myLastPageLoaded = true;
    }
    int loaded = counter.getCount();
    myLoadedCount += loaded;
    return loaded;
  }

  /**
   * Loads the whole query, from the current cursor to the end, or a single page if a
   * positive {@link #setMaxResult(int) max result} was requested.<br>
   * When an optional progress is provided informs it about progress. If an optional activity template is provided - shows current loading state.
   * <p/>
   * There is no percentage any more: without a total count the share of the query
   * already read cannot be computed, so the progress only reports how many issues
   * have been read so far.
   *
   * @param issueHandler issues consumer same as in {@link #loadNext(com.almworks.restconnector.RestSession, com.almworks.restconnector.json.sax.LocationHandler)}
   * @param firstActivity progress activity message shown before the first page is loaded
   * @param nextActivity progress activity pattern shown afterwards. arg1 - number of issues read so far
   * @see #loadNext(com.almworks.restconnector.RestSession, com.almworks.restconnector.json.sax.LocationHandler)
   */
  public void loadAll(RestSession session, LocationHandler issueHandler, @Nullable ProgressInfo progress, @Nullable LocalizedAccessor.Value firstActivity, @Nullable LocalizedAccessor.MessageStr nextActivity) throws ConnectorException {
    while (true) {
      if (progress != null) {
        String message;
        if (myLoadedCount > 0 && nextActivity != null) message = nextActivity.formatMessage(String.valueOf(myLoadedCount));
        else if (myLoadedCount == 0 && firstActivity != null) message = firstActivity.create();
        else message = null;
        if (message != null) progress.startActivity(message);
        else progress.checkCancelled();
      }
      int loaded = loadNext(session, issueHandler);
      if (myLastPageLoaded) break;
      if (loaded <= 0) {
        LogHelper.error("No issues loaded while the server offered another page", myNextPageToken, myLoadedCount, myMaxResult);
        break;
      }
      // An explicit max result caps the load to one page, as it did before the
      // migration: RestDownloadUpdatedIssues.firstSync() sets it to 1 to fetch only
      // the most recently updated issue.
      if (myMaxResult > 0) break;
    }
    if (progress != null) progress.setDone();
  }

  /**
   * Counts the issues passed through to the wrapped handler. {@link PeekArrayElement}
   * opens and closes one JSON document per array element, so counting the document
   * starts counts the elements. The issues themselves are consumed by the SAX
   * handler and never held in memory, so there is nothing else to count.
   */
  private static class CountingHandler implements LocationHandler {
    private final LocationHandler myHandler;
    private int myCount = 0;

    CountingHandler(LocationHandler handler) {
      myHandler = handler;
    }

    @Override
    public void visit(Location what, boolean start, @Nullable String key, @Nullable Object value) throws ParseException, IOException {
      if (what == Location.TOP && start) myCount++;
      myHandler.visit(what, start, key, value);
    }

    public int getCount() {
      return myCount;
    }

    @Override
    public String toString() {
      return "Counting->" + myHandler;
    }
  }
}
