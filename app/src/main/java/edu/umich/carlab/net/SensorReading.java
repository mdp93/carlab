/*
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package edu.umich.carlab.net;

import java.util.ArrayList;
import java.util.List;

/**
 * DTO for OBD readings.
 */
public class SensorReading {
    private double version;
    private String uid;
    private String tripid;
    private ArrayList<Values> values;

    public SensorReading(double version, String uid, String tripid, ArrayList<Values> values) {
        this.version = version;
        this.uid = uid;
        this.tripid = tripid;
        this.values = values;
    }


    public double getVersion() {
        return version;
    }

    public void setVersion(double version) {
        this.version = version;
    }

    public String getUid() {
        return uid;
    }

    public List<Values> getValues() { return values; }

    public void setUid(String uid) {
        this.uid = uid;
    }

    public String getTripidid() {
        return tripid;
    }

    public void setTripid(String tripid) {
        this.tripid = tripid;
    }

    public String toString() {

        /*return "uid:test1;" +
                "lat:" + latitude + ";" +
                "long:" + longitude + ";" +
                "alt:" + altitude + ";" +
                "vin:" + vin + ";" +
                "readings:" + readings.toString().substring(10).replace("}", "").replace(",", ";"); */

        return  "version:" + version + ";" +
                "uid:" + uid + ";" +
                "tripid:" + tripid + ";" +
                "values: [" ;
                //values.toString();
    }
}