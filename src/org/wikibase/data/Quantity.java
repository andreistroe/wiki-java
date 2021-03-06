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

public class Quantity extends WikibaseData {
    private double amount;
    private Item unit;
    private double lowerBound;
    private double upperBound;

    public double getAmount() {
        return amount;
    }

    public void setAmount(double amount) {
        this.amount = amount;
    }

    public Item getUnit() {
        return unit;
    }

    public void setUnit(Item unit) {
        this.unit = unit;
    }

    public double getLowerBound() {
        return lowerBound;
    }

    public void setLowerBound(double lowerBound) {
        this.lowerBound = lowerBound;
    }

    public double getUpperBound() {
        return upperBound;
    }

    public void setUpperBound(double upperBound) {
        this.upperBound = upperBound;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(amount);
        if (null != unit) {
            sb.append(" ");
            sb.append(unit.getEnt());
        }
        return sb.toString();
    }
    
    
    @Override
    public String getDatatype() {
        return "quantity";
    }

    @Override
    public String valueToJSON() {
        StringBuilder sbuild = new StringBuilder();
        sbuild .append('{');
        sbuild.append(String.format("\"amount\": \"%+f\",", amount));
        sbuild.append(String.format("\"unit\": \"http://www.wikidata.org/entity/%s\",", unit.getEnt().getId()));
        sbuild.append(String.format("\"upperBound\": \"%+f\",", upperBound));
        sbuild.append(String.format("\"lowerBound\": \"%+f\"", lowerBound));
        sbuild.append('}');
        return sbuild.toString();
    }

    @Override
    public String toJSON() {
        StringBuilder sbuild = new StringBuilder("{");
        sbuild.append("\"value\":").append(valueToJSON()).append(',');
        sbuild.append("\"type\":\"quantity\"");
        sbuild.append('}');
        return sbuild.toString();
    }
}
