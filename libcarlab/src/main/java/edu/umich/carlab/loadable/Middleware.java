package edu.umich.carlab.loadable;

import edu.umich.carlab.DataMarshal;

import java.util.List;
import java.util.Map;

public interface Middleware {
    String getName();

    Map<String, Float> splitValues(DataMarshal.DataObject dataObject);
}
