<!--
    @(#)editorintersection.jsp 0.01 05/10/2017
    Copyright (C) 2017 MER-C
  
    This is free software: you are free to change and redistribute it under the 
    Affero GNU GPL version 3 or later, see <https://www.gnu.org/licenses/agpl.html> 
    for details. There is NO WARRANTY, to the extent permitted by law.
-->
<%@ include file="security.jspf" %>
<%@ include file="datevalidate.jspf" %>
<%
    request.setAttribute("toolname", "Article/editor intersection");
    request.setAttribute("scripts", new String[] { "common.js", "collapsible.js", "EditorIntersection.js" });

    String wikiparam = ServletUtils.sanitizeForAttributeOrDefault(request.getParameter("wiki"), "en.wikipedia.org");
    String mode = Objects.requireNonNullElse(request.getParameter("mode"), "none");
    String pages = ServletUtils.sanitizeForHTML(request.getParameter("pages"));    
    String category = ServletUtils.sanitizeForAttribute(request.getParameter("category"));
    String user = ServletUtils.sanitizeForAttribute(request.getParameter("user"));
        
    boolean noadmin = (request.getParameter("noadmin") != null);
    boolean nobot = (request.getParameter("nobot") != null);
    boolean noanon = (request.getParameter("noanon") != null);
    boolean nominor = (request.getParameter("nominor") != null);
    boolean noreverts = (request.getParameter("noreverts") != null);
%>
<%@ include file="header.jspf" %>

<p>
This tool retrieves the common editors of a given set of pages. Query limits of
1500 edits/revisions and 25 articles (contributions: most recent, category members: 
first in the GUI) apply.

<form action="./editorintersection.jsp" method=POST>
<table>
<tr>
    <td colspan=2>Wiki:
    <td><input type=text name=wiki value="<%= wikiparam %>" required>
<tr>
    <td><input type=radio name=mode id="radio_cat" value="category"<%= mode.equals("category") ? " checked" : "" %>>
    <td><label for="radio_cat">Category:</label>
    <td><input type=text id=category name=category <%= mode.equals("category") ? "value=\"" + category + "\" required" : "disabled"%>>
<tr>
    <td><input type=radio name=mode id="radio_user" value="contribs"<%= mode.equals("contribs") ? " checked" : "" %>>
    <td><label for="radio_user">Pages edited by:</label>
    <td><input type=text id=user name=user <%= mode.equals("contribs") ? "value=\"" + user + "\" required" : "disabled"%>>
<tr>
    <td valign=top><input type=radio name=mode id="radio_pages" value="pages"<%= mode.equals("pages") ? " checked" : "" %>>
    <td valign=top><label for="radio_pages">Pages:<br>(one per line)</label>
    <td>
        <textarea id=pages name=pages rows=10 <%= mode.equals("pages") ? "required" : "disabled" %>>
<%= pages %>
        </textarea>
<tr>
    <td colspan=2>Exclude: 
    <td><input type=checkbox name=noadmin id="noadmin" value=1<%= (pages.isEmpty() || noadmin) ? " checked" : "" %>>
        <label for="noadmin">admins</label>
        <input type=checkbox name=nobot id="nobot" value=1<%= (pages.isEmpty() || nobot) ? " checked" : "" %>>
        <label for="nobot">bots</label>
        <input type=checkbox name=noanon id="noanon" value=1<%= noanon ? " checked" : "" %>>
        <label for="noanon">IPs</label>
        <input type=checkbox name=nominor id="nominor" value=1<%= nominor ? " checked" : "" %>>
        <label for="nominor">minor edits</label>
        <input type=checkbox name=noreverts id="noreverts" value=1<%= noreverts ? " checked" : "" %>>
        <label for="noreverts">reverts</label>
<tr>
    <td colspan=2>Show changes from:
    <td><input type=date name=earliest value="<%= earliest %>"> to 
        <input type=date name=latest value="<%= latest %>"> (inclusive)
</table>
<br>
<input type=submit value=Search>
</form>

<%
    if (request.getAttribute("error") != null)
    {
%>
<%@ include file="footer.jspf" %>
<%
    }

    Wiki wiki = sessions.sharedSession(wikiparam);
    wiki.setQueryLimit(1500);

    Stream<String> pagestream = switch (mode)
    {
        case "category" -> wiki.getCategoryMembers(category).stream();
        case "contribs" -> wiki.contribs(user, null).stream().map(Wiki.Revision::getTitle);
        case "pages" -> Arrays.stream(pages.split("\r\n")).map(String::trim);
        default ->
        {
%>
<%@ include file="footer.jspf" %>
<%
        }
    };
        
    Set<String> pagesarray = pagestream
        .filter(title -> wiki.namespace(title) >= 0)
        .limit(25)
        .collect(Collectors.toSet());
    if (pagesarray.size() < 2)
    {
        request.setAttribute("error", "Need at least two distinct pages to perform an intersection!");
%>
<%@ include file="footer.jspf" %>
<%
    }
    ArticleEditorIntersector aei = new ArticleEditorIntersector(wiki);
    aei.setIgnoringMinorEdits(nominor);
    aei.setIgnoringReverts(noreverts);
    aei.setDateRange(earliest_odt, latest_odt);
    Map<String, List<Wiki.Revision>> results = aei.intersectArticles(pagesarray, noadmin, nobot, noanon);
    if (results.isEmpty())
    {
        request.setAttribute("error", "No intersection after applying exclusions and removing non-existing pages!");
%>
<%@ include file="footer.jspf" %>
<%
    }
    Map<String, Map<String, List<Wiki.Revision>>> bypage = new HashMap<>();
    results.forEach((key, value) ->
    {
        // group by article
        Map<String, List<Wiki.Revision>> grouppage = value.stream()
            .collect(Collectors.groupingBy(Wiki.Revision::getTitle));
        bypage.put(key, grouppage);
    });
    Pages pageUtils = Pages.of(wiki);
    Revisions revisionUtils = Revisions.of(wiki);
    Users userUtils = Users.of(wiki);

    String blah = bypage.entrySet().stream()
        .sorted((entry1, entry2) -> 
        {
            // sort by number of articles hit
            return entry2.getValue().size() - entry1.getValue().size();
        }).map(entry ->
        {
            // generate HTML
            StringBuilder sb = new StringBuilder();
            sb.append("<h2>");
            sb.append(entry.getKey());
            sb.append("</h2>\n");
            sb.append(userUtils.generateHTMLSummaryLinks(entry.getKey()));

            for (Map.Entry<String, List<Wiki.Revision>> entry2 : entry.getValue().entrySet())
            {
                String thisPage = entry2.getKey();
                List<Wiki.Revision> revs = entry2.getValue();
                StringBuilder title = new StringBuilder(pageUtils.generateSummaryLinks(thisPage));
                title.append(" &ndash; ");
                title.append(revs.size());
                title.append(" edit");
                if (revs.size() > 1)
                    title.append("s");
                sb.append("<p>\n");
                sb.append(ServletUtils.beginCollapsibleSection(title.toString(), true));
                sb.append(revisionUtils.toHTML(revs));
                sb.append(ServletUtils.endCollapsibleSection());
            }
            return sb;
        }).collect(Collectors.joining());
    out.println("<hr>");
    out.println(blah);
%>
<%@ include file="footer.jspf" %>
