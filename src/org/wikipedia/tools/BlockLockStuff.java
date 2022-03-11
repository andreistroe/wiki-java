/**
 *  @(#)BlockLockStuff.java 0.01 14/08/2021
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
package org.wikipedia.tools;

import java.nio.file.*;
import java.time.OffsetDateTime;
import java.util.*;
import org.wikipedia.*;

/**
 *  Utility for dealing with sockpuppets.
 *  @author MER-C
 *  @version 0.01
 */
public class BlockLockStuff
{
    private static WMFWikiFarm sessions = WMFWikiFarm.instance();
    
    public static void main(String[] args) throws Exception
    {
        Wiki enWiki = sessions.sharedSession("en.wikipedia.org");
        // List<String> socks = enWiki.getCategoryMembers("Category:Wikipedia sockpuppets of Bodiadub", true, Wiki.USER_NAMESPACE);
        List<String> socks = Files.readAllLines(Paths.get("spam2.txt"));
        
        lockFinder(socks);
        staleScreener(socks);
        
        // TODO: accept arbitrary input
        // TODO: determine unblocked accounts
        // TODO: determine G5 date - first lock or block as per block list
    }
    
    public static void lockFinder(List<String> socks) throws Exception
    {
        WMFWiki meta = sessions.sharedSession("meta.wikimedia.org");
        System.out.println("Not locked:");
        System.out.println("*{{MultiLock");
        for (String sock : socks)
        {
            // TODO: this is an inefficient way of determining whether an account
            // is locked - there is an additional API call that is still one user = one call
            // but less data transfer. Also, as usual, the W?F can't be arsed doing this
            // properly: https://phabricator.wikimedia.org/T261752
            Map<String, Object> ginfo = sessions.getGlobalUserInfo(sock);
            if (!(Boolean)ginfo.get("locked"))
                System.out.print("|" + meta.removeNamespace(sock));
        }
        System.out.println("}}\n\n");
    }
    
    public static void staleScreener(List<String> socks) throws Exception
    {
        Wiki enWiki = sessions.sharedSession("en.wikipedia.org");
        
        // determine whether accounts are stale
        List<String> notstale = new ArrayList<>();
        List<String> stale = new ArrayList<>();
        List<String> unregistered = new ArrayList<>();
        List<List<Wiki.Revision>> contribs = enWiki.contribs(socks, null, null);
        OffsetDateTime staledate = OffsetDateTime.now().minusDays(91);
        for (int i = 0; i < socks.size(); i++)
        {
            String sock = socks.get(i);
            Wiki.RequestHelper rh = enWiki.new RequestHelper().byUser(sock);
            List<Wiki.LogEntry> socklogs = enWiki.getLogEntries(Wiki.ALL_LOGS, null, rh);
            if (socklogs.isEmpty())
            {
                unregistered.add("*{{checkuser|" + sock + "}}");
                continue;
            }
            
            List<Wiki.Revision> sockcontribs = contribs.get(i);
            OffsetDateTime lastlog = socklogs.get(0).getTimestamp();
            OffsetDateTime lastactive = lastlog;
            if (sockcontribs.size() > 0)
            {
                OffsetDateTime lastedit = sockcontribs.get(0).getTimestamp();
                if (lastedit.isAfter(lastlog))
                    lastactive = lastedit;
            }
            if (lastactive.isAfter(staledate))
                notstale.add("*{{checkuser|" + sock + "}}");
            else
                stale.add("*{{checkuser|" + sock + "}}");
        }
        System.out.println(";Not stale:");
        for (String s : notstale)
            System.out.println(s);
        System.out.println(";Probably stale:");
        for (String s : stale)
            System.out.println(s);
        System.out.println(";Not registered locally");
        for (String s : unregistered)
            System.out.println(s);
    }
}
