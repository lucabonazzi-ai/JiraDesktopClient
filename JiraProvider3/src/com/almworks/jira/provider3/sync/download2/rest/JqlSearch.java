package com.almworks.jira.provider3.sync.download2.rest;

import com.almworks.api.connector.ConnectorException;
import com.almworks.api.constraint.Constraint;
import com.almworks.jira.connector2.JiraException;
import com.almworks.jira.connector2.JiraInternalException;
import com.almworks.jira.provider3.sync.ConnectorManager;
import com.almworks.restconnector.RequestPolicy;
import com.almworks.restconnector.RestResponse;
import com.almworks.restconnector.RestSession;
import com.almworks.restconnector.jql.JqlQuery;
import com.almworks.restconnector.json.ArrayKey;
import com.almworks.util.Env;
import com.almworks.util.LogHelper;
import org.almworks.util.Collections15;
import org.jetbrains.annotations.Nullable;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.ParseException;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Issue search against {@code /rest/api/2/search/jql}.
 * <p/>
 * This endpoint replaced {@code /rest/api/2/search}, which now answers 410 Gone
 * (Atlassian CHANGE-2046). Two things changed for callers: paging is a cursor,
 * {@code nextPageToken}, instead of the {@code startAt} offset, and the response no
 * longer carries a {@code total} count, so the number of matching issues is not
 * known in advance and cannot be known until the last page has been read.
 * <p/>
 * Deliberately the <b>v2</b> flavour of the endpoint. Atlassian published enhanced
 * search under both versions, and v3 is the ADF-aware counterpart of v2 rather than
 * its successor: it returns rich text fields as Atlassian Document Format objects,
 * {@code {"type":"doc","version":1,...}}, where v2 returns plain text. This client
 * parses plain text throughout, and every other call it makes is on {@code api/2},
 * so searching on v3 would hand ADF objects to parsers expecting strings. They log
 * and return null, silently blanking description and other rich text fields.
 *
 * @see com.almworks.jira.provider3.sync.download2.details.RestQueryPager
 */
public class JqlSearch {
  public static final ArrayKey<JSONObject> ISSUES = ArrayKey.objectArray("issues");

  private static final String JQL_MAX_RESULT = "jiraclient.jql.maxresult";

  private static final String PATH = "api/2/search/jql";

  private final JqlQuery myJql;
  /** Cursor for the page to request. Null asks for the first page. */
  private String myNextPageToken = null;
  private int myMaxResult = -1;
  private final List<String> myFields = Collections15.arrayList();

  public JqlSearch(JqlQuery jql) {
    myJql = jql;
  }

  public JqlSearch(Constraint constraint) {
    this(new JqlQuery(constraint));
  }

  public void reset() {
    myNextPageToken = null;
    myMaxResult = -1;
    myFields.clear();
  }

  @SuppressWarnings("unchecked")
  public JSONObject createRequest() {
    JSONObject request = new JSONObject();
    request.put("jql", myJql.getJqlText());
    if (myMaxResult > 0) request.put("maxResults", myMaxResult);
    else LogHelper.error("Missing maxResult");
    if (myNextPageToken != null) request.put("nextPageToken", myNextPageToken);
    if (!myFields.isEmpty()) {
      JSONArray fieldsArray = new JSONArray();
      fieldsArray.addAll(myFields);
      request.put("fields", fieldsArray);
    }
    return request;
  }

  /**
   * @param nextPageToken cursor returned by the previous page, null to ask for the
   * first page
   */
  public void setNextPageToken(@Nullable String nextPageToken) {
    myNextPageToken = nextPageToken;
  }

  public JqlSearch setMaxResult(int maxResult) {
    myMaxResult = maxResult;
    return this;
  }

  public JqlSearch setDefaultMaxResult() {
    int maxResult = Env.getInteger(JQL_MAX_RESULT, 10, 150, 100);
    setMaxResult(maxResult);
    return this;
  }

  public JqlSearch addFields(String ... fields) {
    myFields.addAll(Arrays.asList(fields));
    return this;
  }

  public JqlQuery getJql() {
    return myJql;
  }

  @Override
  public String toString() {
    return "JQLsearch(" + myJql + " token:" + myNextPageToken + " max:" + myMaxResult + " fields:" + myFields + ")";
  }

  public RestResponse request(RestSession session) throws ConnectorException {
    JSONObject request = createRequest();
    RestResponse response = session.restPostJson(PATH, request, RequestPolicy.SAFE_TO_RETRY);
    if (!response.isSuccessful()) LogHelper.warning("Failed to query issues", request); // Hunting a bug - query sometimes fails
    return response;
  }

  /**
   * Utility method to query single issue. Before request sets {@link #setMaxResult(int) max} to 1 other setting are kept.
   * @return single issue or null if no issue returned from server.
   */
  public JSONObject querySingle(RestSession session) throws ConnectorException {
    setMaxResult(1);
    RestResponse response = request(session);
    if (response.getStatusCode() == 400) {
      RestResponse.ErrorResponse error = response.createErrorResponse();
      ConnectorException exception = maybeInaccessibleProject(session, error);
      if (exception != null) throw exception;
      throw error.toException();
    }
    response.ensureSuccessful();
    JSONObject reply;
    try {
      reply = response.getJSONObject();
    } catch (ParseException e) {
      LogHelper.warning("Failed to parse query resule", e);
      throw new JiraInternalException("Failed query issues");
    }
    List<JSONObject> issues = ISSUES.list(reply);
    return issues.isEmpty() ? null : issues.get(0);
  }

  private static final Pattern WRONG_FIELD_VALUE = Pattern.compile("A value with ID '([^']+)' does not exist for the field '([^']+)'");
  public ConnectorException maybeInaccessibleProject(RestSession session, RestResponse.ErrorResponse errorResponse) {
    Matcher m = errorResponse.findMessage(WRONG_FIELD_VALUE);
    if (m == null) return null;
    String valueId = m.group(1);
    String field = m.group(2);
    String displayableValue = null;
    String fieldName = null;
    String description = null;
    if ("project".equals(field)) {
      fieldName = ConnectorManager.LOCAL.getString("remoteQuery.failure.fieldnames.project");
      JSONObject full = RestOperations.projectFull(session, valueId);
      if (full == null) LogHelper.warning("Failed to load wrong project", valueId);
      else displayableValue = JRProject.getDisplayName(full);
      description = ConnectorManager.LOCAL.messageStr("remoteQuery.failure.project.long").formatMessage(displayableValue);
    } else if ("status".equals(field)) {
      fieldName = ConnectorManager.LOCAL.getString("remoteQuery.failure.fieldnames.status");
      try {
        int id = Integer.parseInt(valueId);
        try {
          List<JSONObject> statuses = RestOperations.statuses(session);
          for (JSONObject status : statuses) {
            if (Objects.equals(JRStatus.ID.getValue(status), id)) {
              displayableValue = JRStatus.NAME.getValue(status);
              break;
            }
          }
        } catch (ConnectorException | ParseException e) {
          LogHelper.warning("Failed to load wrong status", valueId);
        }
      } catch (Exception e) {
        // ignore
      }
    }
    if (displayableValue == null) return null;
    String message = ConnectorManager.LOCAL.message2("remoteQuery.failure.fieldValue.short").formatMessage(displayableValue, fieldName);
    if (description == null)
      description = ConnectorManager.LOCAL.message2("remoteQuery.failure.fieldValue.long").formatMessage(displayableValue, fieldName);
    return new JiraException(message, message, description, JiraException.JiraCause.ACCESS_DENIED);
  }
}
