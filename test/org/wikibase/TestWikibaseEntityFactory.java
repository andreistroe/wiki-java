package org.wikibase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.wikibase.data.Claim;
import org.wikibase.data.Entity;
import org.wikibase.data.Item;
import org.wikibase.data.Property;

public class TestWikibaseEntityFactory {

    private Entity readQFromXMLFile(int no) throws IOException, WikibaseEntityParsingException {

        InputStream qstream = ClassLoader.getSystemResourceAsStream("q" + no + ".xml");
        BufferedReader qreader = new BufferedReader(new InputStreamReader(qstream));
        StringBuilder qstring = new StringBuilder();
        try {
            String line = null;
            while (null != (line = qreader.readLine())) {
                qstring.append(line).append(System.lineSeparator());
            }
        } finally {
            if (null != qreader) {
                qreader.close();
            }
        }

        return WikibaseEntityFactory.getWikibaseItem(qstring.toString());
    }

    @Test
    public void testQ3RoLabel() throws IOException, WikibaseException, WikibaseEntityParsingException {
        Entity q3 = readQFromXMLFile(3);
        assertNotNull(q3);
        assertNotNull(q3.getLabels());
        assertEquals(q3.getLabels().get("ro"), "viață");
    }

    @Test
    public void testQ2EnSitelink() throws IOException, WikibaseException, WikibaseEntityParsingException {
        Entity q2 = readQFromXMLFile(2);
        assertNotNull(q2);
        assertNotNull(q2.getSitelinks());
        assertEquals(q2.getSitelinks().get("enwiki").getPageName(), "Earth");
    }

    @Test
    public void testQ2HighestPointItem() throws IOException, WikibaseException, WikibaseEntityParsingException {
        Entity q2 = readQFromXMLFile(2);
        assertNotNull(q2);
        assertNotNull(q2.getClaims());
        Set<Claim> p610claims = q2.getClaims().get(new Property("P610"));
        assertEquals(1, p610claims.size());
        Claim p610claim = new ArrayList<Claim>(p610claims).get(0);

        assertNotNull(p610claim.getMainsnak());
        assertEquals(p610claim.getMainsnak().getDatatype(), "wikibase-item");

        assertTrue(p610claim.getMainsnak().getData() instanceof Item);
        assertEquals("513", ((Item) p610claim.getMainsnak().getData()).getEnt().getId());
    }
    
    @Test
    public void testLoadQ2641808_20251127() throws IOException, WikibaseException, WikibaseEntityParsingException {
        Entity e = readQFromXMLFile(2641808);
        assertNotNull(e);
    }
}
