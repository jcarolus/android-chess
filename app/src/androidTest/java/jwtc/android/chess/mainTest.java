package jwtc.android.chess;

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
        m = new main();
    }

    @After
    public void tearDown() throws Exception {
        m = null;
    }

    @Test
    public void testShowSubViewMenu() throws Exception {
        assertEquals(true, m.engineInfoCount>0);
    }
}