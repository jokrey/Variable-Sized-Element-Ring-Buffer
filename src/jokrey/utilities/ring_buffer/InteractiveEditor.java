package jokrey.utilities.ring_buffer;

import jokrey.utilities.command.line.helper.Argument;
import jokrey.utilities.command.line.helper.CommandLoop;
import jokrey.utilities.ring_buffer.validation.VSBRDebugPrint;
import jokrey.utilities.transparent_storage.bytes.TransparentBytesStorage;
import jokrey.utilities.transparent_storage.bytes.file.FileStorage;
import jokrey.utilities.transparent_storage.bytes.non_persistent.ByteArrayStorage;

import java.io.File;
import java.io.FileNotFoundException;

import static jokrey.utilities.ring_buffer.VarSizedRingBufferQueueOnly.START;
import static jokrey.utilities.ring_buffer.validation.VSBRDebugPrint.elementsToList;
import static jokrey.utilities.ring_buffer.validation.VSBRDebugPrint.reverseElementsToList;

public class InteractiveEditor {
    public static void main(String[] a) throws FileNotFoundException {
        TransparentBytesStorage storage;
        VarSizedRingBufferQueueOnly vsrb;

        CommandLoop loop = new CommandLoop();

        int max = START + loop.readAndParseOr("Enter max storage size(plus "+START+" header) (press <enter> for default):",50,null);

        System.out.println("Enter storage type(empty for RAM, FilePath for file)");
        String storageString = loop.readLine();
        if(storageString==null || storageString.isEmpty())
            storage = new ByteArrayStorage(max);
        else
            storage = new FileStorage(new File(storageString), 8192, "rwd");

        System.out.println("Enter ring type(empty or \"dl\" for normal(double linked, reverse enabled) and \"qo\" for queueonly");
        String vsrbTypeString = loop.readLine();
        if(vsrbTypeString==null || vsrbTypeString.isEmpty() || vsrbTypeString.equalsIgnoreCase("dl"))
            vsrb = new VarSizedRingBuffer(storage, max);
        else
            vsrb = new VarSizedRingBufferQueueOnly(storage, max);



        loop.addCommand("append", "append given data", Argument.with(String.class), args -> {
            vsrb.append(args[0].get().toString().getBytes());
            System.out.println(elementsToList(vsrb, String::new));
        },"a", "add", "+", "enqueue");
        loop.addCommand("print", "print a detailed analysis of the current state, assuming string data",
            Argument.noargs(),
            args -> VSBRDebugPrint.printContents("Command:", vsrb, storage, String::new),
            "details", "p", "?"
        );
        loop.addCommand("list", "print all current elements, converting them to string",
                Argument.noargs(),
                args -> System.out.println(elementsToList(vsrb, String::new)),
                "l", "ls", "!"
        );
        loop.addCommand("first", "print the first element, converting it to string",
                Argument.noargs(),
                args -> System.out.println(new String(vsrb.first())),
                "1", "f", "peek"
        );
        loop.addCommand("dequeue", "print the first element, converting it to string",
                Argument.noargs(),
                args -> {
                    byte[] dequeued = vsrb.dequeue();
                    System.out.println(dequeued==null? null : new String(dequeued));
                    System.out.println(elementsToList(vsrb, String::new));
                },
                "removeAndReturnFirst"
        );
        loop.addCommand("isEmpty", "print the first element, converting it to string",
                Argument.noargs(),
                args -> System.out.println("empty? " + vsrb.isEmpty()),
                "empty"
        );
        loop.addCommand("count", "count all elements, return the number of elements",
                Argument.noargs(),
                args -> System.out.println("size? " + vsrb.size()),
                "num", "size"
        );
        loop.addCommand("deleteFirst", "delete the first element",
                Argument.noargs(),
                args -> {
                    boolean success = vsrb.deleteFirst();
                    System.out.println(success? "deleted" : "could not delete");
                    System.out.println(elementsToList(vsrb, String::new));
                },
                "-", "df"
        );
        loop.addCommand("mem", "prints the memory layout",
            Argument.noargs(),
            args -> VSBRDebugPrint.printMemoryLayout(vsrb, storage, String::new, false),
            "?m", "m"
        );
        loop.addCommand("space", "prints the memory layout",
            Argument.noargs(),
            args -> VSBRDebugPrint.printMemoryLayout(vsrb, storage, String::new, false),
            "memRemaining", "memLeft"
        );


        if(vsrb instanceof VarSizedRingBuffer) {
            VarSizedRingBuffer vsrbDL = (VarSizedRingBuffer) vsrb;
            loop.addCommand("deleteLast", "delete the last element",
                Argument.noargs(),
                args -> {
                    boolean success = vsrbDL.deleteLast();
                    System.out.println(success? "deleted" : "could not delete");
                    System.out.println(elementsToList(vsrb, String::new));
                }, "dl"
            );
            loop.addCommand("listReverse", "delete the last element",
                Argument.noargs(),
                args -> System.out.println(reverseElementsToList(vsrbDL, String::new)),
                "reverse", "lr"
            );
            loop.addCommand("last", "print the last element, converting it to string",
                Argument.noargs(),
                args -> System.out.println(new String(vsrbDL.last())),
                "top"
            );
            loop.addCommand("pop", "print the first element, converting it to string",
                Argument.noargs(),
                args -> {
                    byte[] popped = vsrbDL.pop();
                    System.out.println(popped==null? null : new String(popped));
                    System.out.println(elementsToList(vsrb, String::new));
                },
                "removeAndReturnLast"
            );
        }


        loop.run();
    }
}