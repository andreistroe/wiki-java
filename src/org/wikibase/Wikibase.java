/**
*  This program is free software; you can redistribute it and/or
*  modify it under the terms of the GNU General Public License
*  as published by the Free Software Foundation; either version 3
*  of the License, or (at your option) any later version. Additionally
*  this file is subject to the "Classpath" exception.
*
*  This program is distributed in the hope that it will be useful,
*  but WITHOUT ANY WARRANTY; without even the implied warranty of
*  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
*  GNU General Public License for more details.
*
*  You should have received a copy of the GNU General Public License
*  along with this program; if not, write to the Free Software Foundation,
*  Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
*/
package org.wikibase;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.wikibase.data.Claim;
import org.wikibase.data.Entity;
import org.wikibase.data.Item;
import org.wikibase.data.Property;
import org.wikibase.data.Snak;
import org.wikibase.data.WikibaseData;
import org.wikipedia.Wiki;
import org.xml.sax.SAXException;

public class Wikibase extends Wiki {
    private String queryServiceUrl = "https://query.wikidata.org/sparql";
    private long queryRetryTime = System.currentTimeMillis();

    public Wikibase(String url, String queryServiceUrl) {
        this(url);
        this.queryServiceUrl = queryServiceUrl;
    }

    public Wikibase(String url) {
        super(url, "/w", "https://");
        initVars();
    }

    public Wikibase() {
        this("www.wikidata.org");
    }

    /**
     * Returns an entity identified by the title of a wiki page.
     * 
     * @param site
     * @param pageName
     * @return
     * @throws IOException
     * @throws WikibaseException
     */
    public Entity getWikibaseItemBySiteAndTitle(final String site, final String pageName)
        throws IOException, WikibaseException {
        
        if ("dwiki".equalsIgnoreCase(site)) {
            return getWikibaseItemById(pageName);
        }

        HashMap<String, String> getParams = new HashMap<>();
        HashMap<String, Object> postParams = new HashMap<>();
        getParams.put("action", "wbgetentities");
        getParams.put("sites", site);
        getParams.put("titles", pageName);
        getParams.put("format", "xml");

        final String text = makeApiCall(getParams, postParams, "getWikibaseItemBySiteAndTitle");

        return WikibaseEntityFactory.getWikibaseItem(text);
    }

    /**
     * Returns the entity taken as a parameter, populated with data from wikibase. Use this when you have the Entity object
     * that only contains the ID, which will happen if this entity is reached via another entity's property.
     * 
     * @param item
     * @return
     * @throws IOException
     * @throws WikibaseException
     */
    public Entity getWikibaseItemById(final Entity item) throws IOException, WikibaseException {
        return getWikibaseItemById(item.getId());
    }

    /**
     * Returns the entity associated with the specified wikibase id.
     * 
     * @param item
     * @return
     * @throws IOException
     * @throws WikibaseException
     */
    public Entity getWikibaseItemById(final String id) throws IOException, WikibaseException {
        if (null == id || 0 == id.trim().length())
            return null;
        StringBuilder actualId = new StringBuilder(id.trim());
        if ('q' != Character.toLowerCase(actualId.charAt(0))) {
            if (Pattern.matches("\\d+", actualId)) {
                actualId.insert(0, 'Q');
            } else {
                throw new WikibaseException(id + " is not a valid Wikibase id");
            }
        }
        Map<String, String> getParams = new HashMap<>();
        getParams.put("action", "wbgetentities");
        getParams.put("ids", actualId.toString());
        getParams.put("format", "xml");

        final String text = makeApiCall(getParams, new HashMap<>(), "getWikibaseItem");

        return WikibaseEntityFactory.getWikibaseItem(text);
    }

    /**
     * Retrieves the title of the corresponding page in another site.
     * 
     * @param site
     * @param pageName
     * @param language
     * @return
     * @throws WikibaseException
     * @throws IOException
     */
    public String getTitleInLanguage(final String site, final String pageName, final String language)
        throws WikibaseException, IOException {
        Entity ent = getWikibaseItemBySiteAndTitle(site, pageName);
        return ent.getSitelinks().get(language).getPageName();
    }

    /**
     * Links two pages from different sites via wikibase.
     * 
     * @param fromsite
     * @param fromtitle
     * @param tosite
     * @param totitle
     * @throws IOException
     */
    public void linkPages(final String fromsite, final String fromtitle, final String tosite, final String totitle)
        throws IOException, WikibaseException {
        Map<String, String> getParams1 = new HashMap<>();
        Map<String, Object> postParams1 = new HashMap<>();
        getParams1.put("action", "wbgetentities");
        getParams1.put("sites", tosite);
        getParams1.put("titles", totitle);
        getParams1.put("format", "xml");
        final String text = makeApiCall(getParams1, postParams1, "linkPages");

        final int startindex = text.indexOf("<entity");
        final int endindex = text.indexOf(">", startindex);
        final String entityTag = text.substring(startindex, endindex);
        final StringTokenizer entityTok = new StringTokenizer(entityTag, " ", false);
        String q = null;
        while (entityTok.hasMoreTokens()) {
            final String entityAttr = entityTok.nextToken();
            if (!entityAttr.contains("=")) {
                continue;
            }
            final String[] entityParts = entityAttr.split("\\=");
            if (entityParts[0].trim().startsWith("title")) {
                q = entityParts[1].trim().replace("\"", "");
            }
        }

        if (null == q) {
            return;
        }

        String edittoken = obtainToken();

        HashMap<String, String> getParams = new HashMap<>();
        HashMap<String, Object> postParams = new HashMap<>();
        postParams.put("tosite", tosite);
        postParams.put("totitle", totitle);
        postParams.put("fromtitle", fromtitle);
        postParams.put("fromsite", fromsite);
        postParams.put("token", edittoken);
        postParams.put("format", "xml");

        getParams.put("action", "wblinktitles");

        String res = makeApiCall(getParams, postParams, "linkPages");
        
        DocumentBuilderFactory domBuilderFactory = DocumentBuilderFactory.newInstance();
        try {
            DocumentBuilder builder = domBuilderFactory.newDocumentBuilder();
            Document document = builder.parse(new ByteArrayInputStream(res.getBytes()));
            XPathFactory xpathFactory = XPathFactory.newInstance();
            XPath xPath = xpathFactory.newXPath();
            XPathExpression apiExpression = xPath.compile("/api[1]");
            Node apiNode = (Node) apiExpression.evaluate(document, XPathConstants.NODE);
            if (null == apiNode || null == apiNode.getAttributes()
                || null == apiNode.getAttributes().getNamedItem("success")) {
                throw new WikibaseException("API root node with success parameter not found in text.");
            }
            if (!"1".equals(apiNode.getAttributes().getNamedItem("success").getNodeValue())) {
                XPathExpression errorExpression = xPath.compile("/api[1]/error[1]");
                Node errorNode = (Node) errorExpression.evaluate(document, XPathConstants.NODE);
                if (null != errorNode && null != errorNode.getAttributes()
                    && null != errorNode.getAttributes().getNamedItem("info")) {
                    throw new WikibaseException(errorNode.getAttributes().getNamedItem("info").getNodeValue());
                }
            }
        } catch (Exception e) {
            log(Level.WARNING, "linkPages", e.getMessage());
        }
    }

    public String createItem(Entity createdEntity) throws IOException, WikibaseException {
        throttle();
        
        String edittoken = obtainToken();

        Map<String, String> getParams = new HashMap<>();
        getParams.put("action", "wbeditentity");
        getParams.put("new", "item");

        Map<String, Object> postParams = new HashMap<>();
        postParams.put("data", createdEntity.toJSON());
        postParams.put("clear", "yes");
        postParams.put("token", edittoken);
        postParams.put("format", "xml");

        String text1 = makeApiCall(getParams, postParams, "createItem");
        String ret = null;

        DocumentBuilderFactory domBuilderFactory = DocumentBuilderFactory.newInstance();
        try {
            DocumentBuilder builder = domBuilderFactory.newDocumentBuilder();
            Document document = builder.parse(new ByteArrayInputStream(text1.getBytes()));
            XPathFactory xpathFactory = XPathFactory.newInstance();
            XPath xPath = xpathFactory.newXPath();
            XPathExpression apiExpression = xPath.compile("/api[1]");
            Node apiNode = (Node) apiExpression.evaluate(document, XPathConstants.NODE);
            if (null == apiNode || null == apiNode.getAttributes()
                || null == apiNode.getAttributes().getNamedItem("success")) {
                throw new WikibaseException("API root node with success parameter not found in text.");
            }
            if ("1".equals(apiNode.getAttributes().getNamedItem("success").getNodeValue())) {
                XPathExpression entityExpression = xPath.compile("/api[1]/entity[1]");
                Node entityNode = (Node) entityExpression.evaluate(document, XPathConstants.NODE);
                if (null == entityNode || null == entityNode.getAttributes()
                    || null == entityNode.getAttributes().getNamedItem("id")) {
                    throw new WikibaseException("Entity node not present or without id attribute");
                }
                ret = entityNode.getAttributes().getNamedItem("id").getNodeValue();
            } else {
                XPathExpression errorExpression = xPath.compile("/api[1]/error[1]");
                Node errorNode = (Node) errorExpression.evaluate(document, XPathConstants.NODE);
                if (null != errorNode && null != errorNode.getAttributes()
                    && null != errorNode.getAttributes().getNamedItem("info")) {
                    throw new WikibaseException(errorNode.getAttributes().getNamedItem("info").getNodeValue());
                }
            }
        } catch (Exception e) {
            log(Level.WARNING, "createItem", e.getMessage());
            return null;
        }
        return ret;
    }

    /**
     * Adds specified claim to the entity with the specified ID
     * 
     * @param entityId
     * @param claim
     * @return the guid of the created claim
     * @throws WikibaseException
     * @throws IOException
     */
    public String addClaim(String entityId, Claim claim) throws WikibaseException, IOException {
        throttle();

        String edittoken = obtainToken();

        Map<String, String> getParams = new HashMap<>();
        getParams.put("action", "wbcreateclaim");
        getParams.put("entity", entityId.startsWith("Q") ? entityId : ("Q" + entityId));

        Map<String, Object> postParams = new HashMap<>();
        postParams.put("snaktype", "value");
        postParams.put("property", claim.getProperty().getId());
        postParams.put("value", claim.getValue().valueToJSON());
        postParams.put("token", edittoken);
        postParams.put("format", "xml");
        String text1 = makeApiCall(getParams, postParams, "addClaim");

        DocumentBuilderFactory domBuilderFactory = DocumentBuilderFactory.newInstance();
        String ret = null;
        try {
            DocumentBuilder builder = domBuilderFactory.newDocumentBuilder();
            Document document = builder.parse(new ByteArrayInputStream(text1.getBytes()));
            XPathFactory xpathFactory = XPathFactory.newInstance();
            XPath xPath = xpathFactory.newXPath();
            XPathExpression apiExpression = xPath.compile("/api[1]");
            Node apiNode = (Node) apiExpression.evaluate(document, XPathConstants.NODE);
            if (null == apiNode || null == apiNode.getAttributes()
                || null == apiNode.getAttributes().getNamedItem("success")) {
                throw new WikibaseException("API root node with success parameter not found in text.");
            }
            if ("1".equals(apiNode.getAttributes().getNamedItem("success").getNodeValue())) {
                XPathExpression claimExpression = xPath.compile("/api[1]/claim[1]");
                Node claimNode = (Node) claimExpression.evaluate(document, XPathConstants.NODE);
                if (null == claimNode || null == claimNode.getAttributes()
                    || null == claimNode.getAttributes().getNamedItem("id")) {
                    throw new WikibaseException("Claim node not present or without id attribute");
                }
                ret = claimNode.getAttributes().getNamedItem("id").getNodeValue();
            } else {
                XPathExpression errorExpression = xPath.compile("/api[1]/error[1]");
                Node errorNode = (Node) errorExpression.evaluate(document, XPathConstants.NODE);
                if (null != errorNode && null != errorNode.getAttributes()
                    && null != errorNode.getAttributes().getNamedItem("info")) {
                    throw new WikibaseException(errorNode.getAttributes().getNamedItem("info").getNodeValue());
                }
            }
        } catch (Exception e) {
            log(Level.WARNING, "addClaim", e.getMessage());
            return null;
        }
        return ret;
    }

    /**
     * Edits the specified claim by replacing it with the new one
     * 
     * @param entityId
     * @param claim
     * @return the guid of the created claim
     * @throws WikibaseException
     * @throws IOException
     */
    public String editClaim(Claim claim) throws WikibaseException, IOException {
        throttle();

        String edittoken = obtainToken();

        Map<String, String> getParams = new HashMap<>();
        getParams.put("action", "wbsetclaim");

        Map<String, Object> postParams = new HashMap<>();
        postParams.put("claim", claim.toJSON());
        postParams.put("token", edittoken);
        postParams.put("format", "xml");
        String text1 = makeApiCall(getParams, postParams, "editClaim");

        DocumentBuilderFactory domBuilderFactory = DocumentBuilderFactory.newInstance();
        String ret = null;
        try {
            DocumentBuilder builder = domBuilderFactory.newDocumentBuilder();
            Document document = builder.parse(new ByteArrayInputStream(text1.getBytes()));
            XPathFactory xpathFactory = XPathFactory.newInstance();
            XPath xPath = xpathFactory.newXPath();
            XPathExpression apiExpression = xPath.compile("/api[1]");
            Node apiNode = (Node) apiExpression.evaluate(document, XPathConstants.NODE);
            if (null == apiNode || null == apiNode.getAttributes()
                || null == apiNode.getAttributes().getNamedItem("success")) {
                throw new WikibaseException("API root node with success parameter not found in text.");
            }
            if ("1".equals(apiNode.getAttributes().getNamedItem("success").getNodeValue())) {
                XPathExpression claimExpression = xPath.compile("/api[1]/claim[1]");
                Node claimNode = (Node) claimExpression.evaluate(document, XPathConstants.NODE);
                if (null == claimNode || null == claimNode.getAttributes()
                    || null == claimNode.getAttributes().getNamedItem("id")) {
                    throw new WikibaseException("Claim node not present or without id attribute");
                }
                ret = claimNode.getAttributes().getNamedItem("id").getNodeValue();
            } else {
                XPathExpression errorExpression = xPath.compile("/api[1]/error[1]");
                Node errorNode = (Node) errorExpression.evaluate(document, XPathConstants.NODE);
                if (null != errorNode && null != errorNode.getAttributes()
                    && null != errorNode.getAttributes().getNamedItem("info")) {
                    throw new WikibaseException(errorNode.getAttributes().getNamedItem("info").getNodeValue());
                }
            }
        } catch (Exception e) {
            log(Level.WARNING, "editClaim", e.getMessage());
            return null;
        }
        return ret;
    }

    /**
     * Removes the claim with the specified id from the entity with the specified ID
     * 
     * @param claimId
     * @return the guid of the created claim
     * @throws WikibaseException
     * @throws IOException
     */
    public String removeClaim(String claimId) throws WikibaseException, IOException {
        throttle();

        String edittoken = obtainToken();

        Map<String, String> getParams = new HashMap<>();
        getParams.put("action", "wbremoveclaims");
        Map<String, Object> postParams = new HashMap<>();
        postParams.put("claim", claimId);
        postParams.put("token", edittoken);
        postParams.put("format", "xml");
        String text1 = makeApiCall(getParams, postParams, "removeClaim");

        DocumentBuilderFactory domBuilderFactory = DocumentBuilderFactory.newInstance();
        String ret = null;
        try {
            DocumentBuilder builder = domBuilderFactory.newDocumentBuilder();
            Document document = builder.parse(new ByteArrayInputStream(text1.getBytes()));
            XPathFactory xpathFactory = XPathFactory.newInstance();
            XPath xPath = xpathFactory.newXPath();
            XPathExpression apiExpression = xPath.compile("/api[1]");
            Node apiNode = (Node) apiExpression.evaluate(document, XPathConstants.NODE);
            if (null == apiNode || null == apiNode.getAttributes()
                || null == apiNode.getAttributes().getNamedItem("success")) {
                throw new WikibaseException("API root node with success parameter not found in text.");
            }
            if (!"1".equals(apiNode.getAttributes().getNamedItem("success").getNodeValue())) {
                XPathExpression errorExpression = xPath.compile("/api[1]/error[1]");
                Node errorNode = (Node) errorExpression.evaluate(document, XPathConstants.NODE);
                if (null != errorNode && null != errorNode.getAttributes()
                    && null != errorNode.getAttributes().getNamedItem("info")) {
                    throw new WikibaseException(errorNode.getAttributes().getNamedItem("info").getNodeValue());
                }
            }
        } catch (Exception e) {
            log(Level.WARNING, "removeClaim", e.getMessage());
            return null;
        }
        return ret;
    }

    public String addQualifier(String claimGUID, String propertyId, WikibaseData qualifier)
        throws WikibaseException, IOException {
        throttle();

        String edittoken = obtainToken();

        Map<String, String> getParams = new HashMap<>();
        getParams.put("action", "wbsetqualifier");
        getParams.put("claim", claimGUID);
        getParams.put("property", propertyId.toUpperCase());
        getParams.put("snaktype", "value");
        Map<String, Object> postParams = new HashMap<>();
        postParams.put("value", qualifier.valueToJSON());
        postParams.put("token", edittoken);
        postParams.put("format", "xml");
        String text1 = makeApiCall(getParams, postParams, "addQualifier");
        log(Level.INFO, "addQualifier", text1);
        return null;
    }

    public String addReference(String claimGUID, List<Snak> ref) throws IOException, WikibaseException {
        throttle();

        String edittoken = obtainToken();

        Map<String, String> getParams = new HashMap<>();
        getParams.put("action", "wbsetreference");
        getParams.put("statement", claimGUID);

        StringBuilder snakBuilder = new StringBuilder("{");
        boolean refStarted = false;
        Map<Property, List<Snak>> referenceMap = new HashMap<Property, List<Snak>>();
        for (Snak eachSnak : ref) {
            List<Snak> claimList = referenceMap.get(eachSnak.getProperty());
            if (null == claimList) {
                claimList = new ArrayList<Snak>();
            }
            claimList.add(eachSnak);
            referenceMap.put(eachSnak.getProperty(), claimList);
        }

        for (Entry<Property, List<Snak>> eachRefEntry : referenceMap.entrySet()) {
            if (refStarted) {
                snakBuilder.append(',');
            }
            snakBuilder.append('\"').append(eachRefEntry.getKey().getId()).append("\":");
            snakBuilder.append('[');
            boolean entryStarted = false;
            for (Snak eachSnak : eachRefEntry.getValue()) {
                if (entryStarted) {
                    snakBuilder.append(',');
                }
                snakBuilder.append(eachSnak.toJSON());
                entryStarted = true;
            }
            snakBuilder.append(']');
            refStarted = true;
        }
        snakBuilder.append('}');

        Map<String, Object> postParams = new HashMap<>();
        postParams.put("snaks", snakBuilder.toString());
        postParams.put("token", edittoken);
        postParams.put("format", "xml");
        String text1 = makeApiCall(getParams, postParams, "addReference");
        log(Level.INFO, "addReference", text1);
        return null;
    }

    public void setLabel(String qid, String language, String label) throws IOException, WikibaseException {
        throttle();

        String token = obtainToken();
        Map<String, String> getParams = new HashMap<>();
        getParams.put("action", "wbsetlabel");
        getParams.put("id", qid);

        Map<String, Object> postParams = new HashMap<>();
        postParams.put("language", language);
        postParams.put("value", label);
        postParams.put("token", token);
        postParams.put("format", "xml");

        String text1 = makeApiCall(getParams, postParams, "setLabel");
        log(Level.INFO, "setLabel", text1);
    }

    public void setDescription(String qid, String language, String description) throws IOException, WikibaseException {
        throttle();

        String token = obtainToken();
        Map<String, String> getParams = new HashMap<>();
        getParams.put("action", "wbsetdescription");
        getParams.put("id", qid);

        Map<String, Object> postParams = new HashMap<>();
        postParams.put("language", language);
        postParams.put("value", description);
        postParams.put("token", token);
        postParams.put("format", "xml");
        String text1 = makeApiCall(getParams, postParams, "setDescription");
        log(Level.INFO, "setDescription", text1);
    }

    private String obtainToken() throws IOException, WikibaseException {
        Map<String, String> getParams = new HashMap<>();
        getParams.put("action", "query");
        getParams.put("meta", "tokens");
        getParams.put("format", "xml");
        String text = makeApiCall(getParams, new HashMap<>(), "obtainToken");

        DocumentBuilderFactory domBuilderFactory = DocumentBuilderFactory.newInstance();
        Node tokenNode;
        try {
            DocumentBuilder builder = domBuilderFactory.newDocumentBuilder();
            Document document = builder.parse(new ByteArrayInputStream(text.getBytes()));
            XPathFactory xpathFactory = XPathFactory.newInstance();
            XPath xPath = xpathFactory.newXPath();
            XPathExpression apiExpression = xPath.compile("/api[1]");
            Node apiNode = (Node) apiExpression.evaluate(document, XPathConstants.NODE);
            if (null == apiNode) {
                throw new WikibaseException("API root node not found in text.");
            }
            XPathExpression tokenExpression = xPath.compile("/api[1]/query[1]/tokens[1]");
            tokenNode = (Node) tokenExpression.evaluate(document, XPathConstants.NODE);
        } catch (Exception e) {
            throw new WikibaseException(e);
        }
        if (null == tokenNode || tokenNode.getAttributes() == null
            || tokenNode.getAttributes().getNamedItem("csrftoken") == null) {
            throw new WikibaseException("Token node not found");
        }

        return tokenNode.getAttributes().getNamedItem("csrftoken").getNodeValue();
    }

    public List<Map<String, Object>> query(String queryString) throws IOException, WikibaseException {
        long now = System.currentTimeMillis();
        if (0 < queryRetryTime) {
            while (queryRetryTime > now) {
                log(Level.INFO, "query", String.format("Waiting %d ms until %d...", queryRetryTime - now, queryRetryTime));
                try {
                    Thread.sleep(10000l);
                } catch (InterruptedException e) {
                }
                now = System.currentTimeMillis();
            }
        }

        URL queryService = new URL(queryServiceUrl);
        logurl(queryServiceUrl, "query");
        HttpURLConnection queryServiceConnection = (HttpURLConnection) queryService.openConnection();
        queryServiceConnection.setDoOutput(true);
        // queryServiceConnection.setRequestProperty("Accept", "application/sparql-results+xml");
        queryServiceConnection.setRequestProperty("User-Agent",
            "Wikibase.java/0.36/ https://github.com/andreistroe/wiki-java");

        Map<String, String> parameters = new HashMap<>();
        parameters.put("format", "xml");
        parameters.put("query", queryString);

        queryServiceConnection.setRequestMethod("GET");
        try (DataOutputStream out = new DataOutputStream(queryServiceConnection.getOutputStream())) {
            out.writeBytes(buildParameterString(parameters));
            out.flush();
        }
        log(Level.INFO, "query", "Query string: " + buildParameterString(parameters));

        int status = queryServiceConnection.getResponseCode();
        if (429 == status) {
            String retryAfter = queryServiceConnection.getHeaderField("Retry-After");
            if (null != retryAfter && retryAfter.matches("\\d+")) {
                long throttleTime = Long.parseLong(retryAfter);
                queryRetryTime += throttleTime * 1000l;
            } else if (null != retryAfter) {
                ZonedDateTime zdt = ZonedDateTime.parse(retryAfter, DateTimeFormatter.RFC_1123_DATE_TIME);
                queryRetryTime = zdt.toEpochSecond();
            }
            throw new WikibaseException(String.format("Server too busy. Next allowed retry in %s", retryAfter));
        } else if (status > 299) {
            StringBuilder content = new StringBuilder();
            try (
                BufferedReader reader = new BufferedReader(new InputStreamReader(queryServiceConnection.getErrorStream()))) {
                String inLine = null;
                while (null != (inLine = reader.readLine())) {
                    content.append(inLine).append('\n');
                }
            }
            queryRetryTime = now + 70000l;
            throw new WikibaseException(content.toString());
        } else {
            StringBuilder content = new StringBuilder();
            try (
                BufferedReader reader = new BufferedReader(new InputStreamReader(queryServiceConnection.getInputStream()))) {
                String inLine = null;
                while (null != (inLine = reader.readLine())) {
                    content.append(inLine).append('\n');
                }
            }

            DocumentBuilderFactory domBuilderFactory = DocumentBuilderFactory.newInstance();
            List<Map<String, Object>> resultList = new ArrayList<>();
            try {
                DocumentBuilder builder = domBuilderFactory.newDocumentBuilder();
                Document document = builder.parse(new ByteArrayInputStream(content.toString().getBytes("UTF-8")));

                NodeList resultsNodeList = document.getElementsByTagName("results");
                if (0 < resultsNodeList.getLength()) {
                    Node theResultsNode = resultsNodeList.item(0);
                    Node eachResult = theResultsNode.getFirstChild();
                    while (null != eachResult) {
                        if ("result".equals(eachResult.getNodeName())) {
                            Map<String, Object> result = new HashMap<String, Object>();
                            Node eachBinding = eachResult.getFirstChild();
                            while (null != eachBinding) {
                                if ("binding".equals(eachBinding.getNodeName())) {
                                    String name = eachBinding.getAttributes().getNamedItem("name").getNodeValue();
                                    Node childNode = eachBinding.getFirstChild();
                                    while (null != childNode && Node.ELEMENT_NODE != childNode.getNodeType()) {
                                        childNode = childNode.getNextSibling();
                                    }
                                    String value = childNode.getTextContent();
                                    if ("uri".equalsIgnoreCase(childNode.getNodeName())
                                        && value.startsWith("http://www.wikidata.org/entity/")) {
                                        String qId = value.substring("http://www.wikidata.org/entity/".length());
                                        result.put(name, new Item(new Entity(qId)));
                                    } else {
                                        result.put(name, value);
                                    }
                                }
                                eachBinding = eachBinding.getNextSibling();
                            }
                            if (!result.isEmpty()) {
                                resultList.add(result);
                            }
                        }
                        eachResult = eachResult.getNextSibling();
                    }
                }
                queryRetryTime = now + 70000l;
                return resultList;
            } catch (Exception e) {
                throw new WikibaseException(String.format("Error running query %s", queryString), e);
            }
        }

    }

    private String buildParameterString(Map<String, String> parameters) throws UnsupportedEncodingException {
        StringBuilder result = new StringBuilder();

        for (Map.Entry<String, String> entry : parameters.entrySet()) {
            result.append(URLEncoder.encode(entry.getKey(), "UTF-8"));
            result.append("=");
            result.append(URLEncoder.encode(entry.getValue(), "UTF-8"));
            result.append("&");
        }

        String resultString = result.toString();
        return resultString.length() > 0 ? resultString.substring(0, resultString.length() - 1) : resultString;
    }

    /**
     * Search an entity by a string. It will return a list of possible entity matches that only contain the ID, the label and
     * description. The label and the descriptions are the only things present in the language list, under the key of the
     * language specified when searching.
     * 
     * @param searchString
     * @param lang
     * @return
     * @throws IOException
     * @throws WikibaseException
     */
    public List<Entity> searchWikibase(final String searchString, final String lang) throws IOException, WikibaseException {

        HashMap<String, String> getParams = new HashMap<>();
        HashMap<String, Object> postParams = new HashMap<>();
        getParams.put("action", "wbsearchentities");
        getParams.put("search", searchString);
        getParams.put("language", lang);
        getParams.put("format", "json");

        final String text = makeApiCall(getParams, postParams, "searchWikibase");

        List<Entity> ret;
        try {
            DocumentBuilderFactory domBuilderFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = domBuilderFactory.newDocumentBuilder();
            Document document = builder.parse(new ByteArrayInputStream(text.getBytes()));

            XPathFactory xpathFactory = XPathFactory.newInstance();
            XPath xPath = xpathFactory.newXPath();
            XPathExpression apiExpression = xPath.compile("/api[1]");

            Node apiNode = (Node) apiExpression.evaluate(document, XPathConstants.NODE);

            if (null == apiNode) {
                throw new WikibaseException("API root node not found in text.");
            }

            NamedNodeMap apiAttrs = apiNode.getAttributes();
            Node successAttr = apiAttrs.getNamedItem("success");
            String successStatus = successAttr.getTextContent();
            if (!"1".equals(successStatus)) {
                throw new WikibaseException("API call unsuccessful.");
            }

            Node entitiesNode = null;
            Node eachApiChild = apiNode.getFirstChild();
            while (null != eachApiChild) {
                if ("search".equals(eachApiChild.getNodeName())) {
                    entitiesNode = eachApiChild;
                    break;
                }
                eachApiChild = eachApiChild.getNextSibling();
            }

            if (null == entitiesNode) {
                throw new WikibaseException("Wikibase search node not found.");
            }
            Node presumptiveEntityNode = entitiesNode.getFirstChild();
            Node entityNode = null;
            ret = new ArrayList<>();
            while (null != presumptiveEntityNode) {
                if (!"entity".equals(presumptiveEntityNode.getNodeName())) {
                    continue;
                }
                entityNode = presumptiveEntityNode;

                // parse entity attrs
                NamedNodeMap entityAttrs = entityNode.getAttributes();
                Node missingAttr = entityAttrs.getNamedItem("missing");
                if (null != missingAttr && null != missingAttr.getNodeValue()) {
                    continue;
                }
                String itemId = entityAttrs.getNamedItem("id").getNodeValue();
                Entity entity = new Entity(itemId);
                entity.addLabel(lang, entityAttrs.getNamedItem("label").getNodeValue());
                entity.addDescription(lang, Optional.ofNullable(entityAttrs.getNamedItem("description")).map(Node::getNodeValue).orElse(null));
                ret.add(entity);

                presumptiveEntityNode = presumptiveEntityNode.getNextSibling();
            }
            return ret;
        } catch (XPathExpressionException | DOMException | ParserConfigurationException | SAXException | IOException
            | WikibaseException e) {
            e.printStackTrace();
        }
        return Collections.emptyList();
    }

}
