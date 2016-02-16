package jwtc.android.chess;

import android.util.Log;

import junit.framework.TestCase;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Created by JC Snider on 2/15/2016.
 */
public class GamesListViewTest extends TestCase {
    GamesListView glv;
    @Before
    public void setUp() throws Exception {
        glv = new GamesListView();
    }

    @After
    public void tearDown() throws Exception {
    }

    @Test
    public void testOnOptionsItemSelected() throws Exception {
        //onOptionsItemSelected will return false if it is not handled.
        //We are expecting true, and if the back button menu item worked we would see true
        //Since we see false, this assert fails proving our bug exists
        assertEquals(true,glv.onOptionsItemSelected(null));
    }
}