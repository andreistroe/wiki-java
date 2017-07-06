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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.wikibase.data.Claim;
import org.wikibase.data.Entity;
import org.wikibase.data.Property;
import org.wikibase.data.Snak;
import org.wikibase.data.WikibaseData;
import org.wikipedia.Wiki;

public class Wikibase extends Wiki {

    public Wikibase(String url) {
        super(url);
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
        final StringBuilder url = new StringBuilder(query);
        url.append("action=wbgetentities");
        url.append("&sites=" + site);
        url.append("&titles=" + URLEncoder.encode(pageName, "UTF-8"));
        url.append("&format=xml");

        final String text = fetch(url.toString(), "getWikibaseItem");

        Entity entity = WikibaseEntityFactory.getWikibaseItem(text);
        return entity;
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
        final StringBuilder url = new StringBuilder(query);
        url.append("action=wbgetentities");
        url.append("&ids=").append(actualId);
        url.append("&format=xml");

        final String text = fetch(url.toString(), "getWikibaseItem");

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
        throws IOException {
        final StringBuilder url1 = new StringBuilder(query);
        url1.append("action=wbgetentities");
        url1.append("&sites=" + tosite);
        url1.append("&titles=" + URLEncoder.encode(totitle, "UTF-8"));
        url1.append("&format=xml");
        final String text = fetch(url1.toString(), "linkPages");

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

        final StringBuilder getTokenURL = new StringBuilder(query);
        getTokenURL.append("prop=info");
        getTokenURL.append("&intoken=edit");
        getTokenURL.append("&titles=" + URLEncoder.encode(q, "UTF-8"));
        getTokenURL.append("&format=xml");
        String res = fetch(getTokenURL.toString(), "linkPages");

        final int pagestartindex = res.indexOf("<page ");
        final int pageendindex = res.indexOf(">", pagestartindex);
        final String pageTag = res.substring(pagestartindex, pageendindex);

        String edittoken = null;
        final StringTokenizer pageTok = new StringTokenizer(pageTag, " ", false);
        while (pageTok.hasMoreTokens()) {
            final String pageAttr = pageTok.nextToken();
            if (!pageAttr.contains("=")) {
                continue;
            }
            final String[] entityParts = pageAttr.split("=");
            if (entityParts[0].trim().startsWith("edittoken")) {
                edittoken = entityParts[1].trim().replace("\"", "");
            }
        }
        final StringBuilder postdata = new StringBuilder();
        postdata.append("&tosite=" + tosite);
        postdata.append("&totitle=" + URLEncoder.encode(totitle, "UTF-8"));
        postdata.append("&fromtitle=" + URLEncoder.encode(fromtitle, "UTF-8"));
        postdata.append("&fromsite=" + fromsite);
        postdata.append("&token=" + URLEncoder.encode(edittoken, "UTF-8"));
        postdata.append("&format=xmlfm");

        res = post(query + "action=wblinktitles", postdata.toString(), "linkPages");
    }

    public String createItem(Entity createdEntity) throws IOException, WikibaseException {
        String edittoken = obtainToken();

        final StringBuilder url = new StringBuilder(query);
        url.append("action=wbeditentity");
        url.append("&new=item");
        final StringBuilder postdata = new StringBuilder();
        postdata.append("&data=" + URLEncoder.encode(createdEntity.toJSON(), "UTF-8"));
        postdata.append("&clear=yes");
        postdata.append("&token=" + URLEncoder.encode(edittoken, "UTF-8"));
        postdata.append("&format=xml");
        String text1 = post(url.toString(), postdata.toString(), "createItem");
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
                if (null != errorNode && null != errorNode.getAttributes() && null != errorNode.getAttributes().getNamedItem("info")) {
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
        String edittoken = obtainToken();

        final StringBuilder url = new StringBuilder(query);
        url.append("action=wbcreateclaim");
        url.append("&entity=" + (entityId.startsWith("Q") ? entityId : ("Q" + entityId)));
        final StringBuilder postdata = new StringBuilder();
        postdata.append("&snaktype=value");
        postdata.append("&property=").append(claim.getProperty().getId());
        postdata.append("&value=").append(URLEncoder.encode(claim.getValue().valueToJSON(), "UTF-8"));
        postdata.append("&token=" + URLEncoder.encode(edittoken, "UTF-8"));
        postdata.append("&format=xml");
        String text1 = post(url.toString(), postdata.toString(), "addClaim");

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
                XPathExpression claimExpression = xPath.compile(
                    "/api[1]/claim[1]");
                Node claimNode = (Node) claimExpression.evaluate(document, XPathConstants.NODE);
                if (null == claimNode || null == claimNode.getAttributes()
                    || null == claimNode.getAttributes().getNamedItem("id")) {
                    throw new WikibaseException("Claim node not present or without id attribute");
                }
                ret = claimNode.getAttributes().getNamedItem("id").getNodeValue();
            } else {
                XPathExpression errorExpression = xPath.compile("/api[1]/error[1]");
                Node errorNode = (Node) errorExpression.evaluate(document, XPathConstants.NODE);
                if (null != errorNode && null != errorNode.getAttributes() && null != errorNode.getAttributes().getNamedItem("info")) {
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
        String edittoken = obtainToken();

        final StringBuilder url = new StringBuilder(query);
        url.append("action=wbsetclaim");
        final StringBuilder postdata = new StringBuilder();
        postdata.append("&claim=").append(URLEncoder.encode(claim.toJSON(), "UTF-8"));
        postdata.append("&token=" + URLEncoder.encode(edittoken, "UTF-8"));
        postdata.append("&format=xml");
        String text1 = post(url.toString(), postdata.toString(), "editClaim");

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
                XPathExpression claimExpression = xPath.compile(
                    "/api[1]/claim[1]");
                Node claimNode = (Node) claimExpression.evaluate(document, XPathConstants.NODE);
                if (null == claimNode || null == claimNode.getAttributes()
                    || null == claimNode.getAttributes().getNamedItem("id")) {
                    throw new WikibaseException("Claim node not present or without id attribute");
                }
                ret = claimNode.getAttributes().getNamedItem("id").getNodeValue();
            } else {
                XPathExpression errorExpression = xPath.compile("/api[1]/error[1]");
                Node errorNode = (Node) errorExpression.evaluate(document, XPathConstants.NODE);
                if (null != errorNode && null != errorNode.getAttributes() && null != errorNode.getAttributes().getNamedItem("info")) {
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
        String edittoken = obtainToken();

        final StringBuilder url = new StringBuilder(query);
        url.append("action=wbremoveclaims");
        final StringBuilder postdata = new StringBuilder();
        postdata.append("&claim=" + claimId);
        postdata.append("&token=" + URLEncoder.encode(edittoken, "UTF-8"));
        postdata.append("&format=xml");
        String text1 = post(url.toString(), postdata.toString(), "removeClaim");

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
                if (null != errorNode && null != errorNode.getAttributes() && null != errorNode.getAttributes().getNamedItem("info")) {
                    throw new WikibaseException(errorNode.getAttributes().getNamedItem("info").getNodeValue());
                }
            }
        } catch (Exception e) {
            log(Level.WARNING, "removeClaim", e.getMessage());
            return null;
        }
        return ret;
    }
    public String addQualifier(String claimGUID, String propertyId, WikibaseData qualifier) throws WikibaseException, IOException {
        String edittoken = obtainToken();

        final StringBuilder url = new StringBuilder(query);
        url.append("action=wbsetqualifier");
        url.append("&claim=" + claimGUID);
        url.append("&property=" + propertyId.toUpperCase());
        url.append("&snaktype=value");
        final StringBuilder postdata = new StringBuilder();
        postdata.append("&value=" + URLEncoder.encode(qualifier.valueToJSON(), "UTF-8"));
        postdata.append("&token=" + URLEncoder.encode(edittoken, "UTF-8"));
        postdata.append("&format=xml");
        String text1 = post(url.toString(), postdata.toString(), "addQualifier");
        log(Level.INFO, "addQualifier", text1);
        return null;
    }
    
    public String addReference(String claimGUID, List<Snak> ref) throws IOException, WikibaseException {
        String edittoken = obtainToken();
        
        final StringBuilder url = new StringBuilder(query);
        url.append("action=wbsetreference");
        url.append("&statement=" + claimGUID);
        
        StringBuilder snakBuilder = new StringBuilder("{");
        boolean refStarted = false;
        Map<Property, List<Snak>> referenceMap = new HashMap<Property, List<Snak>>();
        for (Snak eachSnak: ref) {
            List<Snak> claimList = referenceMap.get(eachSnak.getProperty());
            if (null == claimList) {
                claimList = new ArrayList<Snak>();
            }
            claimList.add(eachSnak);
            referenceMap.put(eachSnak.getProperty(), claimList);
        }
        
        for (Entry<Property, List<Snak>> eachRefEntry: referenceMap.entrySet()) {
            if (refStarted) {
                snakBuilder.append(',');
            }
            snakBuilder.append('\"').append(eachRefEntry.getKey().getId()).append("\":");
            snakBuilder.append('[');
            boolean entryStarted = false;
            for (Snak eachSnak: eachRefEntry.getValue()) {
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
        
        final StringBuilder postdata = new StringBuilder();
        postdata.append("&snaks=" + URLEncoder.encode(snakBuilder.toString(), "UTF-8"));
        postdata.append("&token=" + URLEncoder.encode(edittoken, "UTF-8"));
        postdata.append("&format=xml");
        String text1 = post(url.toString(), postdata.toString(), "addReference");
        log(Level.INFO, "addReference", text1);
        return null;
    }

    public void setLabel(String qid, String language, String label) throws IOException, WikibaseException {
        String token = obtainToken();
        final StringBuilder url = new StringBuilder(query);
        url.append("action=wbsetlabel");
        url.append("&id=" + qid);

        final StringBuilder postdata = new StringBuilder();
        postdata.append("&language=" + language);
        postdata.append("&value=" + URLEncoder.encode(label, "UTF-8"));
        postdata.append("&token=" + URLEncoder.encode(token, "UTF-8"));
        postdata.append("&format=xml");
        String text1 = post(url.toString(), postdata.toString(), "setLabel");
        log(Level.INFO, "setLabel", text1);
    }
    
    public void setDescription(String qid, String language, String label) throws IOException, WikibaseException {
        String token = obtainToken();
        final StringBuilder url = new StringBuilder(query);
        url.append("action=wbsetdescription");
        url.append("&id=" + qid);

        final StringBuilder postdata = new StringBuilder();
        postdata.append("&language=" + language);
        postdata.append("&value=" + URLEncoder.encode(label, "UTF-8"));
        postdata.append("&token=" + URLEncoder.encode(token, "UTF-8"));
        postdata.append("&format=xml");
        String text1 = post(url.toString(), postdata.toString(), "setDescription");
        log(Level.INFO, "setDescription", text1);
    }
    
    private String obtainToken() throws IOException, WikibaseException {
        StringBuilder url1 = new StringBuilder(query);
        url1.append("action=query");
        url1.append("&meta=tokens");
        url1.append("&format=xml");
        String text = fetch(url1.toString(), "obtainToken");

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
        String edittoken = tokenNode.getAttributes().getNamedItem("csrftoken").getNodeValue();
        return edittoken;
    }
}