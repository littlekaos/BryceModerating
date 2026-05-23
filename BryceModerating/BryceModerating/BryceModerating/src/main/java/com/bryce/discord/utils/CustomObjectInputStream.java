package com.bryce.discord.utils;

import com.bryce.discord.models.WarnRecord;

import java.io.*;

public class CustomObjectInputStream extends ObjectInputStream {

    public CustomObjectInputStream(InputStream in) throws IOException {
        super(in);
    }

    @Override
    protected ObjectStreamClass readClassDescriptor() throws IOException, ClassNotFoundException {
        ObjectStreamClass resultClassDescriptor = super.readClassDescriptor();

        if (resultClassDescriptor.getName().equals("BryceModerating.Moderating$WarnRecord")) {
            return ObjectStreamClass.lookup(WarnRecord.class);
        }
        return resultClassDescriptor;
    }
}


