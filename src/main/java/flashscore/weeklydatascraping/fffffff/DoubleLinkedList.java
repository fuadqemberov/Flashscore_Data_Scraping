package flashscore.weeklydatascraping.fffffff;

import java.util.Objects;

public class DoubleLinkedList {
    Node2 head;
    Node2 tail;

    public void add(int data) {
        Node2 eleman = new Node2(data);

        if (head == null) {
            head = eleman;
            tail = eleman;
        } else {
            if (head.next == null) {
                head.next = eleman;
                eleman.prev = head;
                tail = eleman;
            } else {
                eleman.prev = tail;
                tail.next = eleman;
                tail = eleman;
            }
        }
    }

    public void addWithIndex(int data, int index) {
        Node2 eleman = new Node2(data);

        if (head == null) {
            head = eleman;
            tail = eleman;
        } else {
            if (head != null && index==0) {
                eleman.next = head;
                head.prev = eleman;
                head = eleman;
                tail = head.next;
            } else {
                Node2 temp  = head;

                int i =0;
                while(i<index){
                    temp = temp.next;
                    i++;
                }
                if(temp==null){
                   eleman.next = temp;
                   temp.prev = eleman;

                   temp.prev.next = eleman;
                   eleman.prev = temp.prev;

                }
                eleman.next = temp;
                temp.prev.next = eleman;

                eleman.prev = temp.prev;
                temp.prev = eleman;

            }
        }
    }



    public void delete(){
        if(Objects.isNull(head)){
            System.out.println("There is no element for delete !");
        } else if (Objects.isNull(head.next)) {
            head = null;
        }
        else {
            tail = tail.prev;
            tail.next =null;
        }
    }


    public void deleteWithIndex(int index) {
        if (Objects.isNull(head)) {
            System.out.println("There is no element for delete !");
        }
        else if (Objects.isNull(head.next) && index==0) {
            head = null;
            tail=null;
        }
        else{
            Node2 temp  = head;

            int i =0;
            while(i<index){
                temp = temp.next;
                i++;
            }
            if(temp.next == null){
                tail = tail.prev;
                tail.next =null;
            } else {
                temp.prev.next = temp.next;
                temp.next.prev = temp.prev;
            }
        }
    }

    public void print() {
        Node2 temp = head;
        System.out.print("begin -> ");
        while (temp != null) {
            System.out.print(temp.data + " -> ");
            temp = temp.next;
        }
        System.out.print("last");
    }

    public void reversePrint() {
        Node2 temp = tail;
        while (temp != null) {
            System.out.print(temp.data + " -> ");
            temp = temp.prev;
        }
        System.out.print("last");
    }
}
