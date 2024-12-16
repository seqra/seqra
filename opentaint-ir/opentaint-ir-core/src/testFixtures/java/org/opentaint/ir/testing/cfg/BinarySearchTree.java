package org.opentaint.ir.testing.cfg;

import kotlin.NotImplementedError;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class BinarySearchTree<T extends Comparable<T>> extends AbstractSet<T> implements SortedSet<T> {

    private static class Node<T> {
        final T value;
        Node<T> left = null;
        Node<T> right = null;

        Node(T value) {
            this.value = value;
        }
    }

    private Node<T> root = null;

    private int size = 0;

    private boolean deletionResult;

    @Override
    public int size() {
        return size;
    }

    private Node<T> find(T value) {
        if (root == null) return null;
        return find(root, value);
    }

    private Node<T> find(Node<T> start, T value) {
        int comparison = value.compareTo(start.value);
        if (comparison == 0) {
            return start;
        }
        else if (comparison < 0) {
            if (start.left == null) return start;
            return find(start.left, value);
        }
        else {
            if (start.right == null) return start;
            return find(start.right, value);
        }
    }

    private Node<T> minimumValue(Node<T> root) {
        if (root.left == null) {
            return new Node<>(root.value);
        }
        else {
            return minimumValue(root.left);
        }
    }

    @Override
    public boolean contains(Object o) {
        @SuppressWarnings("unchecked")
        T t = (T) o;
        Node<T> closest = find(t);
        return closest != null && t.compareTo(closest.value) == 0;
    }

    @Override
    public boolean add(T t) {
        Node<T> closest = find(t);
        int comparison = closest == null ? -1 : t.compareTo(closest.value);
        if (comparison == 0) {
            return false;
        }
        Node<T> newNode = new Node<>(t);
        if (closest == null) {
            root = newNode;
        }
        else if (comparison < 0) {
            assert closest.left == null;
            closest.left = newNode;
        }
        else {
            assert closest.right == null;
            closest.right = newNode;
        }
        size++;
        return true;
    }

    @Override
    public boolean remove(Object o) {
        deletionResult = false;
        T delValue = (T) o;
        root = removeAt(root, delValue);
        return deletionResult;
    }

    private Node<T> removeAt(Node<T> root, T value) {
        if (root == null) {
            return null;
        }

        int difference = value.compareTo(root.value);
        if (difference < 0) {
            root.left = removeAt(root.left, value);
        }
        else if (difference > 0) {
            root.right = removeAt(root.right, value);
        }
        else {
            if (!deletionResult) {
                size--;
                deletionResult = true;
            }
            if (root.left == null && root.right == null) {
                root = null;
            }
            else if (root.left != null && root.right != null) {
                Node<T> rootLeft = root.left;
                Node<T> rootRight = root.right;
                root = minimumValue(root.right);
                root.left = rootLeft;
                root.right = removeAt(rootRight, root.value);
            }
            else if (root.left != null) {
                root = root.left;
            }
            else {
                root = root.right;
            }
        }
        return root;
    }

    @Nullable
    @Override
    public Comparator<? super T> comparator() {
        return null;
    }

    @NotNull
    @Override
    public Iterator<T> iterator() {
        return new BinarySearchTreeIterator(root);
    }

    public class BinarySearchTreeIterator implements Iterator<T> {
        Stack<Node<T>> stack = new Stack<>();
        T currentRootValue;

        private BinarySearchTreeIterator(Node<T> root) {
            pushToStack(root);
        }

        @Override
        public boolean hasNext() {
            return !stack.empty();
        }

        @Override
        public T next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            Node<T> root = stack.pop();
            currentRootValue = root.value;
            pushToStack(root.right);
            return root.value;
        }

        private void pushToStack(Node<T> root) {
            if (root != null) {
                stack.push(root);
                pushToStack(root.left);
            }
        }

        @Override
        public void remove() {
            if (currentRootValue == null) {
                throw new IllegalStateException();
            }
            BinarySearchTree.this.remove(currentRootValue);
            currentRootValue = null;
        }
    }

    @NotNull
    @Override
    public SortedSet<T> subSet(T fromElement, T toElement) {
        throw new NotImplementedError();
    }

    @NotNull
    @Override
    public SortedSet<T> headSet(T toElement) {
        throw new NotImplementedError();
    }

    @NotNull
    @Override
    public SortedSet<T> tailSet(T fromElement) {
        throw new NotImplementedError();
    }

    @Override
    public T first() {
        if (root == null) throw new NoSuchElementException();
        Node<T> current = root;
        while (current.left != null) {
            current = current.left;
        }
        return current.value;
    }

    @Override
    public T last() {
        if (root == null) throw new NoSuchElementException();
        Node<T> current = root;
        while (current.right != null) {
            current = current.right;
        }
        return current.value;
    }

    public int height() {
        return height(root);
    }

    private int height(Node<T> node) {
        if (node == null) return 0;
        return 1 + Math.max(height(node.left), height(node.right));
    }

    public boolean checkInvariant() {
        return root == null || checkInvariant(root);
    }

    private boolean checkInvariant(Node<T> node) {
        Node<T> left = node.left;
        if (left != null && (left.value.compareTo(node.value) >= 0 || !checkInvariant(left))) return false;
        Node<T> right = node.right;
        return right == null || right.value.compareTo(node.value) > 0 && checkInvariant(right);
    }

}
