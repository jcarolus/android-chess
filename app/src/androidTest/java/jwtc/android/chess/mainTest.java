package jwtc.android.chess;

import android.content.Context;
import android.content.Intent;
import android.app.Activity;

import junit.framework.TestCase;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;


import static org.junit.Assert.*;

/**
 * Created by Jim on 2/18/2016.
 */
public class mainTest extends TestCase {
    main m;

    @Before
    public void setUp() throws Exception {
        //Intent settings = new Intent(getApplicationContext(),
        m = new main();
        m.getApplicationContext();
        Intent settings = new Intent( m.getApplicationContext(), ChessActivity.class);
        //startActivity();
        Activity a = new Activity();
        a.startActivity(settings);
    }

    @After
    public void tearDown() throws Exception {
        m = null;
    }

    @Test
    public void testShowSubViewMenu() throws Exception {
        m.showSubViewMenu();
        m.engineInfoAlertDialog.getButton(0).performClick();
        assertEquals(true, m.engineInfoCount > 0);
    }
}