package edu.umich.carlab.io;

public interface ObdProgressListener {

    void stateUpdate(final ObdCommandJob job);

}