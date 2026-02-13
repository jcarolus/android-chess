package jwtc.android.chess.tools;

import java.util.ArrayList;

import jwtc.android.chess.services.GameApi;
import jwtc.android.chess.services.HMap;

public class ImportApi extends GameApi {
    // @TODO not static!
    protected static ArrayList<HMap.Pair> hashMap;

    public void resetHashMap() {
        hashMap = new ArrayList<>();
    }

    public boolean addToHashMap(long hash, String name) {
        int n = hashMap.size();
        boolean duplicate = false;
        for (int j = 0; j < n; j++) {
            HMap.Pair p = hashMap.get(j);
            if (p.hash == hash) {
                duplicate = true;
                break;
            }
        }
        if (!duplicate) {
            hashMap.add(new HMap.Pair(hash, name));
            return true;
        }
        return false;
    }

    public ArrayList<HMap.Pair> getHashMap() {
        return hashMap;
    }
}
