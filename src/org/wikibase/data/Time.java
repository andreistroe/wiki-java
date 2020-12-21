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

import java.math.BigInteger;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.chrono.IsoEra;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

public class Time extends WikibaseData {
    private int before;
    private int after;
    private int precision;
    private int timezone = 0;
    private YearMonth yearMonth = null;
    private LocalDate date;
    private LocalTime localTime;

    public YearMonth getYearMonth() {
        return yearMonth;
    }

    public void setYearMonth(YearMonth yearMonth) {
        this.yearMonth = yearMonth;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public LocalTime getLocalTime() {
        return localTime;
    }

    public void setLocalTime(LocalTime localTime) {
        this.localTime = localTime;
    }

    private URL calendarModel;

    public Time() {
        super();
        try {
            calendarModel = new URL("http://www.wikidata.org/entity/Q1985727");
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
    }

    private long year;

    @Override
    public String getDatatype() {
        return "time";
    }

    public int getBefore() {
        return before;
    }

    public void setBefore(int before) {
        this.before = before;
    }

    public int getAfter() {
        return after;
    }

    public void setAfter(int after) {
        this.after = after;
    }

    public URL getCalendarModel() {
        return calendarModel;
    }

    public void setCalendarModel(URL calendarModel) {
        this.calendarModel = calendarModel;
    }

    public int getTimezone() {
        return timezone;
    }

    public void setTimezone(int timezone) {
        this.timezone = timezone;
    }

    public int getPrecision() {
        return precision;
    }

    public void setPrecision(int precision) {
        this.precision = precision;
    }

    public long getYear() {
        return year;
    }

    public void setYear(long year) {
        this.year = year;
    }

    public String toString() {
        String[] patternsForOldYears = new String[] { "% billion years", // precision: billion years
            "$100 million years", // precision: hundred million years
            "$10 million years", // precision: ten million years
            "$1 million years", // precision: million years
            "$100,000 years", // precision: hundred thousand years
            "$10,000 years", // precision: ten thousand years
        };
        Map<Integer, DateTimeFormatter> dateformats = new HashMap<Integer, DateTimeFormatter>() {
            {
                put(11, DateTimeFormatter.ofPattern("d MMMM y G"));
                put(12, DateTimeFormatter.ofPattern("d MMMM y G HH 'hours' Z"));
                put(13, DateTimeFormatter.ofPattern("d MMMM y G HH:mm Z"));
                put(14, DateTimeFormatter.ofPattern("d MMMM y G HH:mm:ss Z"));
            }
        };
        if (5 >= precision) {
            long factor = BigInteger.valueOf(10l).pow(9 - precision).longValue();
            long[] yy = new long[] { year / factor, year % factor };
            long y2 = yy[0];
            if (yy[1] != 0) {
                y2 = yy[0] + 1l;
            }
            return String.format(patternsForOldYears[precision], y2);
        }
        String era = "";
        if (null != date) {
            era = date.getEra() == IsoEra.BCE ? " BC" : " AD";
        } else {
            if (null != yearMonth) {
                year = yearMonth.getYear();
            }
            if (year < 1000l) {
                era = year < 0l ? " BC" : " AD";
            }
        }

        StringBuilder builder = new StringBuilder();
        switch (precision) {
        case 6:
            builder.append(Math.floor((Math.abs(year) - 1l) / 1000.0) + 1);
            builder.append(" millenium");
            builder.append(era);
            break;
        case 7:
            builder.append(Math.floor((Math.abs(year) - 1l) / 100.0) + 1);
            builder.append(" century");
            builder.append(era);
            break;
        case 8:
            builder.append(Math.floor(Math.abs((double) year) / 10) * 10);
            builder.append("s");
            builder.append(era);
            break;
        case 9:
            builder.append(year);
            builder.append(era);
            break;
        case 10:
            builder.append(yearMonth.format(DateTimeFormatter.ofPattern("MMMM y G")));
            break;
        default:
            builder.append(date.format(dateformats.get(precision)));

        }
        return builder.toString();
    }

    @Override
    public String valueToJSON() {
        StringBuilder sbuild = new StringBuilder("{");
        DateTimeFormatter isoFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mmZ");

        sbuild.append("\"precision\":").append(precision);
        sbuild.append(',');
        sbuild.append("\"before\":").append(before);
        sbuild.append(',');
        sbuild.append("\"after\":").append(after);
        sbuild.append(',');
        sbuild.append("\"timezone\":").append(timezone);
        sbuild.append(',');

        sbuild.append("\"time\":\"");
        if (precision > 10) {
            sbuild.append(date.getEra() == IsoEra.BCE ? '-' : '+')
                .append(date.format(isoFormatter));
        } else if (precision == 10) {
            sbuild.append(yearMonth.getYear() < 0 ? '-' : '+')
                .append(yearMonth.format(DateTimeFormatter.ofPattern("yyyy-MM")))
                .append("-00T00:00:00Z");
        } else {
            sbuild.append(year >= 0l ? "+" : "").append(year).append("-00-00T00:00:00Z");
        }
        sbuild.append('\"');
        sbuild.append(',');
        sbuild.append("\"calendarmodel\":\"").append(calendarModel.toString()).append('\"');
        sbuild.append('}');
        return sbuild.toString();
    }
}
