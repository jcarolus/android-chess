package jwtc.chess;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

/** Recorded moves, independent of the board currently being viewed. Child zero is the main continuation. */
public final class GameTree {
    public static final class Node {
        private final Node parent;
        private final PGNEntry entry;
        private final int ply;
        private final int boardState;
        private final int turn;
        private int finalState = -1;
        private final ArrayList<Node> children = new ArrayList<>();
        private final ArrayList<Integer> nags = new ArrayList<>();
        private String leadingComment = "";

        private Node(Node parent, PGNEntry entry, int boardState, int turn) {
            this.parent = parent;
            this.entry = copy(entry);
            this.ply = parent == null ? 0 : parent.ply + 1;
            this.boardState = boardState;
            this.turn = turn;
        }

        public Node getParent() { return parent; }
        public int getPly() { return ply; }
        public int getBoardState() { return boardState; }
        public int getTurn() { return turn; }
        public int getFinalState() { return finalState; }
        public PGNEntry getEntry() { return copy(entry); }
        public List<Node> getChildren() { return Collections.unmodifiableList(children); }
        public List<Integer> getNags() { return Collections.unmodifiableList(nags); }
        public String getLeadingComment() { return leadingComment; }
        public Node getNext() { return children.isEmpty() ? null : children.get(0); }
    }

    private final Node root;
    private final String initialFen;
    private final int startingPly;
    private String rootComment = "";

    public GameTree(String initialFen, int boardState, int turn) {
        this.initialFen = initialFen;
        String[] fields = initialFen.split("\\s+");
        int fullmove = fields.length > 5 ? Integer.parseInt(fields[5]) : 1;
        startingPly = 2 * (Math.max(1, fullmove) - 1) + ("b".equals(fields[1]) ? 1 : 0);
        root = new Node(null, null, boardState, turn);
    }

    public Node getRoot() { return root; }
    public String getInitialFen() { return initialFen; }
    public String getRootComment() { return rootComment; }
    public void setRootComment(String text) { rootComment = text == null ? "" : text; }

    /** Node identity is stable until deletion or reset; detached and foreign nodes are rejected. */
    public boolean contains(Node node) {
        if (node == null) return false;
        while (node.parent != null) {
            if (!node.parent.children.contains(node)) return false;
            node = node.parent;
        }
        return node == root;
    }

    public Node append(Node parent, PGNEntry entry, int boardState, int turn, boolean reuse) {
        requireMember(parent);
        if (reuse) {
            for (Node child : parent.children) {
                if (child.entry.move == entry.move && child.entry.duckMove == entry.duckMove) return child;
            }
        }
        Node child = new Node(parent, entry, boardState, turn);
        parent.finalState = -1;
        parent.children.add(child);
        return child;
    }

    /** Resignation, time forfeit or agreed draw belongs to one line's endpoint. */
    public boolean setFinalState(Node node, int state) {
        requireMember(node);
        if (node.getNext() != null) return false;
        node.finalState = state;
        return true;
    }

    public void setAnnotation(Node node, String text) {
        requireMember(node);
        if (node == root) setRootComment(text);
        else node.entry.sAnnotation = text == null ? "" : text;
    }

    public void setLeadingComment(Node node, String text) {
        requireMember(node);
        node.leadingComment = text == null ? "" : text;
    }

    public void addNag(Node node, int nag) {
        requireMember(node);
        if (node == root || nag < 0 || nag > 255) throw new IllegalArgumentException("Invalid NAG");
        node.nags.add(nag);
    }

    public boolean isMainLine(Node node) {
        if (!contains(node)) return false;
        while (node.parent != null) {
            if (node.parent.getNext() != node) return false;
            node = node.parent;
        }
        return true;
    }

    public Node mainLineEnd() {
        Node node = root;
        while (node.getNext() != null) node = node.getNext();
        return node;
    }

    public Node mainLineAt(int ply) {
        if (ply < 0) return null;
        Node node = root;
        while (node != null && node.ply < ply) node = node.getNext();
        return node;
    }

    public List<Node> mainLine() {
        ArrayList<Node> nodes = new ArrayList<>();
        for (Node node = root.getNext(); node != null; node = node.getNext()) nodes.add(node);
        return Collections.unmodifiableList(nodes);
    }

    public List<Node> pathTo(Node node) {
        requireMember(node);
        ArrayList<Node> path = new ArrayList<>();
        for (; node.parent != null; node = node.parent) path.add(node);
        Collections.reverse(path);
        return path;
    }

    public boolean deleteVariation(Node node) {
        if (!contains(node) || node == root || node.parent.getNext() == node) return false;
        return node.parent.children.remove(node);
    }

    /** Explicit truncation for rejected online/puzzle moves, not ordinary history navigation. */
    public boolean removeContinuation(Node node) {
        if (!contains(node) || node == root) return false;
        return node.parent.children.remove(node);
    }

    public boolean promoteVariation(Node node) {
        if (!contains(node) || node == root || node.parent.getNext() == node) return false;
        node.parent.children.remove(node);
        node.parent.children.add(0, node);
        return true;
    }

    public String moveNumber(Node node) {
        requireMember(node);
        int absolutePly = startingPly + node.ply - 1;
        return (absolutePly / 2 + 1) + (absolutePly % 2 == 0 ? "." : "...");
    }

    /** Iterative traversal also handles very long lines without consuming the Java call stack. */
    public String exportMoves() {
        StringBuilder out = new StringBuilder();
        appendComment(out, rootComment);
        Deque<Object> work = new ArrayDeque<>();
        pushContinuations(work, root);
        while (!work.isEmpty()) {
            Object item = work.pop();
            if (item instanceof String) {
                out.append(item);
            } else if (item instanceof Node) {
                Node node = (Node) item;
                appendComment(out, node.leadingComment);
                out.append(moveNumber(node)).append(' ').append(node.entry.sMove);
                if (node.entry.duckMove != -1) out.append('@').append(Pos.toString(node.entry.duckMove));
                out.append(' ');
                for (int nag : node.nags) out.append('$').append(nag).append(' ');
                appendComment(out, node.entry.sAnnotation);
            } else {
                pushContinuations(work, ((Continuation) item).node);
            }
        }
        return out.toString().trim();
    }

    private static final class Continuation {
        final Node node;
        Continuation(Node node) { this.node = node; }
    }

    private static void pushContinuations(Deque<Object> work, Node parent) {
        if (parent.children.isEmpty()) return;
        Node first = parent.getNext();
        work.push(new Continuation(first));
        for (int i = parent.children.size() - 1; i > 0; i--) {
            Node alternative = parent.children.get(i);
            work.push(") ");
            work.push(new Continuation(alternative));
            work.push(alternative);
            work.push("( ");
        }
        work.push(first);
    }

    private static void appendComment(StringBuilder out, String text) {
        if (text != null && !text.isEmpty()) {
            out.append('{').append(text.replace('{', '[').replace('}', ']')).append("} ");
        }
    }

    private void requireMember(Node node) {
        if (!contains(node)) throw new IllegalArgumentException("Node is not in this game");
    }

    private static PGNEntry copy(PGNEntry entry) {
        if (entry == null) return null;
        PGNEntry result = new PGNEntry(entry.sMove, entry.sAnnotation == null ? "" : entry.sAnnotation,
            entry.move, entry.duckMove);
        result.finalState = entry.finalState;
        return result;
    }
}
