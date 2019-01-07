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

/**
 * DTO for OBD readings.
 */
public class Values {
    private long time;
    private String device;
    private String sensor;
    private float data;

    public Values(long time,
                  String device, String sensor, float data) {
        this.time = time;
        this.device = device;
        this.sensor = sensor;
        this.data = data;
    }

    public long getTimestamp() {
        return time;
    }

    public void setTimestamp(long time) {
        this.time = time;
    }

    public String getDevice() {
        return device;
    }

    public void setDevice(String device) { this.device = device; }

    public float getData() {
        return data;
    }

    public String getSensor() {
        return sensor;
    }

    public void setSensor(String sensor) {
        this.sensor = sensor;
    }

    public void setData(float data) {
        this.data = data;
    }

    public String toString() {

        return  "{" +
                "time:" + time + ";" +
                "device:" + device + ";" +
                "sensor:" + sensor + ";" +
                "data:" + data + ";" +
                "},";
    }
}