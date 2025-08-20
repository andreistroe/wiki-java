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

import java.net.URI;
import java.util.Optional;

public class URLData extends WikibaseData {
    private URI url;
    private String rawUrl;

    public URLData(URI url) {
        super();
        this.url = url;
    }

    public URLData(String urlValue)
    {
        super();
        this.rawUrl = urlValue;
    }

    public URI getUrl() {
        return url;
    }

    public void setUrl(URI url) {
        this.url = url;
    }

    @Override
    public String toString() {
        return "URLData [url=" + getURLString() + "]";
    }

    @Override
    public String getDatatype() {
        return "string";
    }

    @Override
    public String valueToJSON() {
        return "\"" + getURLString() + "\"";
    }

    public String getURLString() {
        return Optional.ofNullable(url).map(Object::toString).orElse(rawUrl);
    }
    
    public boolean equals(Object obj)
    {
        if (this == obj)
            return true;
        if (!(obj instanceof URLData))
            return false;
        URLData other = (URLData) obj;
        return getURLString().equals(other.getURLString());
    }
}
