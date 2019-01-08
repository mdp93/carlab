package edu.umich.carlab.loadable;

import edu.umich.carlab.DataMarshal;

import java.util.List;

public interface Middleware {
    String getName();

    App generateApp();

    List<DataMarshal.DataObject> splitValues(DataMarshal.DataObject dataObject);
}
