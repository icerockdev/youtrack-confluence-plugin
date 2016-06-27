package jetbrains.macros;

import com.atlassian.confluence.pages.Page;
import com.atlassian.confluence.pages.PageManager;
import com.atlassian.confluence.renderer.PageContext;
import com.atlassian.confluence.renderer.radeox.macros.MacroUtils;
import com.atlassian.confluence.util.velocity.VelocityUtils;
import com.atlassian.renderer.RenderContext;
import com.atlassian.renderer.v2.RenderMode;
import com.atlassian.renderer.v2.macro.MacroException;
import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory;
import com.atlassian.sal.api.transaction.TransactionTemplate;
import com.opensymphony.webwork.ServletActionContext;
import jetbrains.macros.base.YouTrackAuthAwareMacroBase;
import jetbrains.macros.util.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import youtrack.Issue;
import youtrack.Project;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class IssueReport extends YouTrackAuthAwareMacroBase {
    private static final Logger LOG = LoggerFactory.getLogger(IssueReport.class);
    private final PageManager pageManager;

    public IssueReport(PluginSettingsFactory pluginSettingsFactory,
                       TransactionTemplate transactionTemplate,
                       PageManager pageManager) {
        super(pluginSettingsFactory, transactionTemplate);
        this.pageManager = pageManager;
    }

    public boolean isInline() {
        return false;
    }

    public boolean hasBody() {
        return false;
    }

    public RenderMode getBodyRenderMode() {
        return RenderMode.NO_RENDER;
    }

    private int intValueOf(final String str, int defaultValue) {
        if (str == null) return defaultValue;
        try {
            return Integer.valueOf(str);
        } catch (NumberFormatException nfe) {
            return defaultValue;
        }
    }

    public String execute(Map params, String body, RenderContext renderContext)
            throws MacroException {
        try {
            final Map<String, Object> context = MacroUtils.defaultVelocityContext();
            final String project = (String) params.get(Strings.PROJECT);
            final String query = (String) params.get(Strings.QUERY);
            final StringBuilder result = new StringBuilder();
            if (project != null && query != null) {
                final int retries = Integer.valueOf(getProperty(Strings.RETRIES, "10"));
                final Project prj = tryGetItem(youTrack.projects, project, retries);
                if (prj != null) {
                    final StringBuilder rows = new StringBuilder();
                    final int pageSize = intValueOf((String) params.get(Strings.PAGE_SIZE), 25);
                    final HttpServletRequest request = ServletActionContext.getRequest();
                    final int currentPage = request == null ? 1 : intValueOf(request.getParameter(Strings.PAGINATION_PARAM), 1);
                    final int numPages = intValueOf((String) params.get(Strings.TOTAL_PAGES), 10);
                    final StringBuilder pagination = new StringBuilder();
                    final PageContext pageContext = (PageContext) renderContext;
                    final Page page = pageManager.getPage(pageContext.getSpaceKey(), pageContext.getPageTitle());
                    final Map<String, Object> myContext = new HashMap<String, Object>();
                    final int startIssue = currentPage == 1 ? 0 : (currentPage - 1) * pageSize + 1;
                    final List<Issue> issues = youTrack.issues.query("project: " + project + " " + query, startIssue, pageSize);
                    for (final Issue sIssue : issues) {
                        myContext.clear();
                        myContext.putAll(context);
                        final Issue issue = sIssue.createSnapshot();
                        myContext.put(Strings.ISSUE, sIssue.getId());
                        myContext.put(Strings.STYLE, (issue.isResolved()) ? "line-through" : "normal");
                        myContext.put(Strings.BASE, getProperty(Strings.HOST).replace(Strings.REST_PREFIX, Strings.EMPTY));
                        myContext.put(Strings.STATE, issue.getState());
                        myContext.put(Strings.SUMMARY, issue.getSummary());
                        myContext.put(Strings.ASSIGNEE, issue.getAssignee() != null ? issue.getAssignee().getFullName() : Strings.UNASSIGNED);
                        rows.append(VelocityUtils.getRenderedTemplate(Strings.ROW, myContext));
                    }
                    if(page != null) {
                        final String thisPageUrl = page.getUrlPath();

                        for (int i = 1; i <= numPages; i++) {
                            myContext.clear();
                            myContext.putAll(context);
                            myContext.put("num", String.valueOf(i));
                            myContext.put("param", Strings.PAGINATION_PARAM);
                            myContext.put("url", thisPageUrl);
                            myContext.put("style", i == currentPage ? "font-weight:bold;" : "font-weight:normal;");
                            pagination.append(VelocityUtils.getRenderedTemplate(Strings.PAGINATION_SINGLE, myContext));
                        }
                    }
                    context.put("pagination", request != null ? pagination.toString() : Strings.EMPTY);
                    context.put("rows", rows.toString());
                    context.put("hasIssues", issues.size() > 0 ? String.valueOf(true) : null);
                    context.put("title", query + " from " + project);
                    result.append(VelocityUtils.getRenderedTemplate(Strings.BODY_REPORT, context));
                } else result.append("Project ").append(project).append(" not found");
            }
            return result.toString();
        } catch (Exception ex) {
            LOG.error("YouTrack report macro encounters error", ex);
            throw new MacroException(ex);
        }
    }
}