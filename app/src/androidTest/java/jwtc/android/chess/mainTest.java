package jwtc.android.chess;

import android.content.Context;
import android.content.Intent;
import android.app.Activity;
import android.test.ActivityInstrumentationTestCase2;

import junit.framework.TestCase;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;


import static org.junit.Assert.*;

/**
 * Created by Jim on 2/18/2016.
 */
public class mainTest extends ActivityInstrumentationTestCase2<main> {

    main myActivity;
    public mainTest() {
        super("com.stuff",main.class);
    }

    @Before
    public void setUp() throws Exception {
        myActivity = getActivity();

    }

    @After
    public void tearDown() throws Exception {
        //m = null;
    }

    @Test
    public void testShowSubViewMenu() throws Exception {
        myActivity.showSubViewMenu();
        //Simulate button press
        myActivity.engineInfoAlertDialog.getListView().performItemClick(myActivity.engineInfoAlertDialog.getListView().getAdapter().getView(0,null,null),
                0,myActivity.engineInfoAlertDialog.getListView().getAdapter().getItemId(0));
        assertEquals(true, myActivity.engineInfoCount > 0);
    }
}