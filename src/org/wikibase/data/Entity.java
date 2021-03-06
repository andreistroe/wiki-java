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
package org.wikibase.data;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * This class represents the Wikibase entity (item) with the list of labels, descriptions, claims, sitelinks.
 * 
 * @author acstroe
 *
 */
public class Entity {

    public Entity(String id) {
        super();
        this.id = id;
    }

    private Map<String, String> labels = new HashMap<String, String>();
    private Map<String, String> descriptions = new HashMap<String, String>();
    private String id;
    private Map<Property, Set<Claim>> claims = new HashMap<Property, Set<Claim>>();
    private Map<String, Sitelink> sitelinks = new HashMap<String, Sitelink>();
    private boolean loaded;

    public Map<String, String> getLabels() {
        return Collections.unmodifiableMap(labels);
    }

    public Map<String, String> getDescriptions() {
        return Collections.unmodifiableMap(descriptions);
    }

    public void addLabel(String key, String value) {
        labels.put(key, value);
    }

    public void addDescription(String key, String value) {
        descriptions.put(key, value);
    }

    public String getId() {
        return id;
    }

    public Map<Property, Set<Claim>> getClaims() {
        return Collections.unmodifiableMap(claims);
    }

    public void addClaim(Property prop, Claim claim) {
        if (null == claim) {
            return;
        }
        Set<Claim> propClaims = claims.get(prop);
        if (null == propClaims) {
            propClaims = new LinkedHashSet<Claim>();
            claims.put(prop, propClaims);
        }
        propClaims.add(claim);
    }

    public Map<String, Sitelink> getSitelinks() {
        return Collections.unmodifiableMap(sitelinks);
    }

    public void addSitelink(Sitelink link) {
        sitelinks.put(link.getSite(), link);
    }

    public void setLoaded(boolean b) {
        loaded = b;
    }

    public boolean isLoaded() {
        return loaded;
    }
    
    public Set<Claim> getClaims(Property prop) {
        if (null == claims || !claims.containsKey(prop)) {
            return null;
        }
        return claims.get(prop);
    }
    
    public Set<Claim> getBestClaims(Property prop) {
        if (null == claims || !claims.containsKey(prop)) {
            return null;
        }
        Set<Claim> propClaims = claims.get(prop);
        
        Map<Rank, List<Claim>> claimsByRank = propClaims.stream().collect(Collectors.groupingBy(Claim::getRank));
        Optional<Rank> maxRank = claimsByRank.keySet().stream().max(Comparator.naturalOrder());
        return claimsByRank.get(maxRank.get()).stream().collect(Collectors.toSet());
    }

    @Override
    public String toString() {
        return id + ": " + (0 == labels.size() ? "not loaded" : (labels.get("en") + " (" + descriptions.get("en") + ")"));
    }
    
    public String toJSON() {
        StringBuilder sbuild = new StringBuilder("{");
        boolean started = false;
        if (null != labels && !labels.isEmpty()) {
            started = true;
            sbuild.append("\"labels\": {");
            boolean firstLabelDone = false;
            for (Map.Entry<String, String> eachLabelEntry: labels.entrySet()) {
                if (firstLabelDone) {
                    sbuild.append(',');
                }
                sbuild.append("\"").append(eachLabelEntry.getKey()).append("\":{");
                sbuild.append("\"language\":\"").append(eachLabelEntry.getKey()).append("\",");
                sbuild.append("\"value\":").append("\"").append(eachLabelEntry.getValue()).append("\"");
                sbuild.append("}");
                firstLabelDone = true;
            }
            sbuild.append("}");
        }
        if (null != descriptions && !descriptions.isEmpty()) {
            if (started) {
                sbuild.append(',');
            }
            started = true;
            sbuild.append("\"descriptions\": {");
            boolean firstDescriptionDone = false;
            for (Map.Entry<String, String> eachDescriptionEntry: descriptions.entrySet()) {
                if (firstDescriptionDone) {
                    sbuild.append(',');
                }
                sbuild.append("\"").append(eachDescriptionEntry.getKey()).append("\":{");
                sbuild.append("\"language\":\"").append(eachDescriptionEntry.getKey()).append("\",");
                sbuild.append("\"value\":").append("\"").append(eachDescriptionEntry.getValue()).append("\"");
                sbuild.append("}");
                firstDescriptionDone = true;
            }
            sbuild.append("}");
        }
        if (null != sitelinks && !sitelinks.isEmpty()) {
            if (started) {
                sbuild.append(',');
            }
            started = true;
            sbuild.append("\"sitelinks\": {");
            boolean firstSitelinkDone = false;
            for (Map.Entry<String, Sitelink> eachSitelinkEntry: sitelinks.entrySet()) {
                if (firstSitelinkDone) {
                    sbuild.append(',');
                }
                sbuild.append("\"").append(eachSitelinkEntry.getKey()).append("\":");
                sbuild.append(eachSitelinkEntry.getValue().toJSON());
                firstSitelinkDone = true;
            }
            sbuild.append("}");
        }
        if (null != claims && !claims.isEmpty()) {
            if (started) {
                sbuild.append(',');
            }
            started = true;
            sbuild.append("\"claims\": [");
            boolean firstClaimSetDone = false;
            for (Map.Entry<Property, Set<Claim>> eachClaimListEntry: claims.entrySet()) {
                if (firstClaimSetDone) {
                    sbuild.append(',');
                }
                boolean firstClaimDone = false;
                for (Claim eachClaim: eachClaimListEntry.getValue()) {
                    if (firstClaimDone) {
                        sbuild.append(',');
                    }
                    sbuild.append(eachClaim.toJSON());
                    firstClaimDone = true;
                }
                firstClaimSetDone = true;
            }
            sbuild.append("]");
        }

        sbuild.append("}");
        return sbuild.toString();
    }
}
