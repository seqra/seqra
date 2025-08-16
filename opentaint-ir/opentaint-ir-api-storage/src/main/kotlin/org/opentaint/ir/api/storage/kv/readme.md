## Pluggable Key/Value Storage API

The Pluggable Key/Value Storage API layer provides the lowest-level data access API available. Implementations of
the API are being provided using a Service Provider Interface, `PluggableKeyValueStorageSPI`. Any particular
key/value storage is an implementation of `PluggableKeyValueStorage`. It can be created by particular Service
Provider, an implementation of `PluggableKeyValueStorageSPI`.

`PluggableKeyValueStorage` encapsulates one or more named maps (`NamedMap`) that contain data. This encapsulation
lets you perform read and modify operations against multiple stores within a single transaction. `NamedMap` is a
named collection of key/value pairs. If a `NamedMap` is not allowed to contain duplicate keys, then it is a map.
Otherwise, it is a multi-map. Also, `NamedMap` can be thought as a table with two columns, one for keys and another
for values. Both keys and values are managed as byte arrays. You can use cursors to iterate over a `NamedMap`, for
example, to find the leftmost key/value pair by a key. All operations can only be performed within a transaction.

Both keys and values are considered as comparable byte arrays, with natural lexicographic byte order. In JVM, this
byte order is equivalent to comparison of two unsigned integers taken from two bytes, i.e. for bytes `b0` and `b1`:

```kotlin
val cmp = (b0.toInt() and 0xff).compareTo((b1.toInt() and 0xff))
```

- [Transactions](#transactions)
- [CRUD operations](#crud-operations)
- [Cursors](#cursors)

### Transactions

Transaction is required for any access to data in the database. In general, particular transaction and isolation
guarantees depend on implementation of `PluggableKeyValueStorage`. But unless it is specified differently, any
transaction holds a database snapshot (a version of the database), thus providing snapshot isolation.

Transactions can be read-only or not. Use read-only transactions to read and not update data.

```kotlin
val roTxn = storage.beginReadonlyTransaction() // start read-only transaction
val txn = storage.beginTransaction() // start read-write transaction
```

Any transaction should be finished, meaning that it is either aborted or committed. To finish a read-only transaction,
abort it. Transaction is `Closeable`, so it can be just closed. Closing read-only transaction aborts it, closing
read-write commits it.

Transactions can be created and closed implicitly by use of the `PluggableKeyValueStorage.transactional()` and
`PluggableKeyValueStorage.readonlyTransactional()` methods accepting lambdas:

```kotlin
storage.transactional { txn ->
    // do smth in read-write transaction
}
```

```kotlin
storage.readonlyTransactional { txn ->
    // do smth in read-only transaction
}
```

### CRUD operations

Pluggable Key/Value Storage API provides CRUD operations for key/value pairs. All operations require a transaction
object. All operations deal with binary data only, represented as byte arrays.

To create a pair in a named map, invoke `put` method:

```kotlin
txn.put("a map", byteArrayOf(0), byteArrayOf(1))
```

The same call should be used to update a value by key, if the named map doesn't support key duplicates. If it does,
then one more value by specified key is being added. The `put` method return `true` if a modification was actually
done.

To get a value by key in a named map, use `get` method:

```kotlin
val value = txn.get("a map", byteArrayOf(0))
```

It returns value as byte array or `null` if no such key is put in the map. This method should be used only for named
maps without key duplicates. To iterate several values by a single key in a named map with key duplicates,
use [Cursors](#cursors).

To delete a value by key in a named map without key duplicates, use `delete` method:

```kotlin
txn.delete("a map", byteArrayOf(0))
```

The method returns `true` if the key/value pair was actually deleted.

In a named map with key duplicates, deletion can only be performed by specifying both key and value:

```kotlin
txn.delete("a map", byteArrayOf(0), byteArrayOf(1))
```

The method returns `true` if the key/value pair was actually deleted.

Described CRUD methods has overloaded ones with `NamedMap` parameter instead of string name of map. From performance
point of view, use of these methods is preferable in a "batch" mode - when several operations are done against the
single named map.

### Cursors

Use Cursors to iterate over key/value pairs of a named map in forward and backward directions. When iterating in forward
direction, all next key/value pairs would be greater than previous ones according to lexicographic natual byte order.

To obtain a cursor that doesn't navigate to a key/value pair, invoke the `navigateTo` method of transaction:

```kotlin
val cursor = txn.navigateTo("a map")
```

To iterate through all key/value pairs of the map in forward direction, write something like the following:

```kotlin
while (cursor.moveNext()) {
    cursor.key    // get a key in current position
    cursor.value  // get a value in current position
}
```

To iterate through all key/value pairs of the map in backward direction:

```kotlin
while (cursor.movePrev()) {
    cursor.key    // get a key in current position
    cursor.value  // get a value in current position
}
```

To obtain a cursor that navigates to a key/value pair, invoke the `navigateTo` method of transaction with key specified:

```kotlin
val cursor = txn.navigateTo("a map", byteArrayOf(0))
```

If there is no key/value pair with such key in the named map, `cursor.moveNext()` would return `false`, so no key/value
pair would be iterated by the loop above. If there is a pair with the specified key in the named map, the cursor would
navigate to the leftmost pair, and the loop above would iterate over all key/value pairs available in the named map
with the specified key.