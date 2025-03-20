package flashscore.weeklydatascraping.fffffff;


import java.util.Objects;

public class BinaryTree {
    TNode root;

    public BinaryTree() {
        this.root = null;
    }

    TNode newtNode(int data) {
        root = new TNode(data);
        return root;
    }

    TNode insert(TNode root, int data) {

        if (Objects.nonNull(root)) {
            if (data < root.data) {
                root.left = insert(root.left, data);
            } else {
                root.right = insert(root.right, data);
            }
        } else {
            root = newtNode(data);
        }
        return root;
    }

    void printInOrder(TNode node) {
        if (node != null) {
            printInOrder(node.left); // Left subtree
            System.out.print(node.data + " "); // Root
            printInOrder(node.right); // Right subtree
        }
    }

    public int height(TNode root) {
        if (root == null) {
            return 0;
        } else {
            int sol = height(root.left);
            int sag = height(root.right);
            System.out.println("sol ve sag : " + sol + " & " + sag);

            if (sol >= sag) {
                 return sol + 1;
             } else {
                 return sag + 1;
             }
        }
    }

    public int findHeight(TNode  aNode) {
        if (aNode == null) {
            return -1;
        }

        int lefth = findHeight(aNode.left);
        int righth = findHeight(aNode.right);

        if (lefth > righth) {
            return lefth + 1;
        } else {
            return righth + 1;
        }
    }


    void deleteKey(int data) {
        root = deleteRec(root, data);
    }

    // Özyinelemeli silme metodu
    TNode deleteRec(TNode root, int data) {
        // Ağaç boşsa veya yaprak düğüme ulaşıldıysa
        if (root == null)
            return root;

        // Silinecek düğümü bulma
        if (data < root.data)
            root.left = deleteRec(root.left, data);
        else if (data > root.data)
            root.right = deleteRec(root.right, data);
        else {
            // Silinecek düğüm bulundu

            // Durum 1: Yaprak düğüm veya tek çocuklu düğüm
            if (root.left == null)
                return root.right;
            else if (root.right == null)
                return root.left;

            // Durum 2: İki çocuklu düğüm
            // Sağ alt ağaçtaki en küçük değeri bul
            root.data = minValue(root.right); // root.data =  21
            // En küçük değeri sil
            root.right = deleteRec(root.right, root.data);

        }

        return root;
    }

    int minValue(TNode root) {
        int minv = root.data;  //23
        while (root.left != null) {
            minv = root.left.data; //21
            root = root.left;  // 22 olur 21  yer deyishir
        }
        return minv; //21
    }


}
