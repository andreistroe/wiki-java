/**
 *  @(#)WMFWikiFarm.java 0.01 28/08/2021
 *  Copyright (C) 2021-20XX MER-C and contributors
 *
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

package org.wikipedia;

import java.time.OffsetDateTime;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 *  Tests for WMFWikiFarm.
 *  @author MER-C
 */
public class WMFWikiFarmTest
{
    private final WMFWikiFarm sessions = new WMFWikiFarm();
    
    @ParameterizedTest
    @CsvSource({"enwiki, en.wikipedia.org",  "wikidatawiki, www.wikidata.org",
            "zhwikiquote, zh.wikiquote.org", "commonswiki, commons.wikimedia.org",
            "metawiki, meta.wikimedia.org"})
    public void newSessionFromDBName(String dbname, String domain)
    {
        assertEquals(domain, WMFWikiFarm.dbNameToDomainName(dbname));
    }
    
    @Test
    public void getGlobalUserInfo() throws Exception
    {
        // locked account with local block
        // https://meta.wikimedia.org/w/index.php?title=Special:CentralAuth&target=Uruguymma
        Map<String, Object> guserinfo = sessions.getGlobalUserInfo("Uruguymma");
        assertTrue((Boolean)guserinfo.get("locked"));
        assertEquals(38, guserinfo.get("editcount"));
        assertEquals(OffsetDateTime.parse("2016-09-21T13:59:30Z"), guserinfo.get("registration"));
        assertEquals(Collections.emptyList(), guserinfo.get("groups"));
        assertEquals(Collections.emptyList(), guserinfo.get("rights"));
        assertEquals("enwiki", guserinfo.get("home"));
        
        // enwiki
        Map luserinfo = (Map)guserinfo.get("enwiki");
        assertEquals("https://en.wikipedia.org", luserinfo.get("url"));
        assertEquals(23, luserinfo.get("editcount"));
        assertEquals(OffsetDateTime.parse("2016-09-21T13:59:29Z"), luserinfo.get("registration"));
        assertEquals(Collections.emptyList(), luserinfo.get("groups"));
        assertTrue((Boolean)luserinfo.get("blocked"));
        assertNull(luserinfo.get("blockexpiry"));
        assertEquals("Abusing [[WP:Sock puppetry|multiple accounts]]: Please see: "
            + "[[w:en:Wikipedia:Sockpuppet investigations/Japanelemu]]", luserinfo.get("blockreason"));
        
        // meta
        luserinfo = (Map)guserinfo.get("metawiki");
        assertEquals("https://meta.wikimedia.org", luserinfo.get("url"));
        assertEquals(0, luserinfo.get("editcount"));
        assertEquals(OffsetDateTime.parse("2016-09-21T13:59:37Z"), luserinfo.get("registration"));
        assertEquals(Collections.emptyList(), luserinfo.get("groups"));
        assertFalse((Boolean)luserinfo.get("blocked"));
        assertNull(luserinfo.get("blockexpiry"));
        assertNull(luserinfo.get("blockreason"));
        
        // global and local groups set
        // https://meta.wikimedia.org/wiki/Special:CentralAuth?target=Jimbo+Wales
        guserinfo = sessions.getGlobalUserInfo("Jimbo Wales");
        assertEquals(List.of("founder"), guserinfo.get("groups"));
        luserinfo = (Map)guserinfo.get("enwiki");
        assertEquals(List.of("checkuser", "founder", "oversight", "sysop"), luserinfo.get("groups"));
        
        // IP address (throws UnknownError)
        // guserinfo = sessions.getGlobalUserInfo("127.0.0.1");
        // assertNull(guserinfo);
    }

    @Test
    public void sharedSession()
    {
        WMFWiki wiki1 = sessions.sharedSession("en.wikipedia.org");
        WMFWiki wiki2 = sessions.sharedSession("en.wikipedia.org");
        assertTrue(wiki1 == wiki2); // must be same object
    }
}
