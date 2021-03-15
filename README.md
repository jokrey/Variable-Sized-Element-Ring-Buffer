# Variable Sized Element Ring Buffer

Your new capacitated queue/stack/iterable. Automatically discarding most-early elements when a certain size limit is reached.

Atomicity and consistency are guaranteed.

### Installing

* Clone the repository with the 'git clone' command for now. (may turn this into a maven repo at some point)

## Features

* This ring buffer supports fifo queue operations (peek, enqueue, dequeue) for variable sized byte arrays,
but keeps below a maximum size deleting the earliest element automatically.

* Optionally this ring buffer also supports stack operations(top, push and pop).

* Additionally, it supports iteration (optionally reverse), making it a full, capacitated, linked list (with the exception that deletion is only possible at end or start).

* Able to operate on any transparent storage

* atomicity guarantees for all operations, given that underlying transparent storage maintains those

## Uses

* Typically, this is part of another database system, where we use it as a log
* Generally speaking it can obviously be used in any situation where you have limited space and/
  or non-constant/decreasing interest in list-like, queue-like or stack-like data over time.

## Usage

See the java doc commentary on the api's themselves, or refer to the tests for examples.

## License

This project is licensed under the MIT License - see the [LICENSE.md](LICENSE.md) file for details