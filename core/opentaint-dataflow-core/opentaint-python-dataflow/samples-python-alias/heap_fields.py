from helpers import alias_sink


def positive_read_pair_a(p):
    dst = p.a
    # alias: arg0.a
    alias_sink(dst)


def positive_write_then_read(p, src):
    p.a = src
    dst = p.a
    # alias: arg0.a, arg1
    alias_sink(dst)


def positive_nested_write_read(n, src):
    n.box.data = src
    dst = n.box.data
    # alias: arg0.box.data, arg1
    alias_sink(dst)


def positive_node_next_data(nd):
    dst = nd.next.data
    # alias: arg0.next.data
    alias_sink(dst)


def positive_node_next_next(nd):
    dst = nd.next.next
    # alias: arg0.next.next
    alias_sink(dst)


def positive_field_to_local(b):
    v = b.data
    x = v
    # alias: arg0.data
    alias_sink(x)


def positive_two_field_reads(b, src):
    b.data = src
    x = b.data
    # alias: arg0.data, arg1
    alias_sink(x)
    y = b.data
    # alias: arg0.data, arg1
    alias_sink(y)


def positive_cond_field_write(b, src, c):
    if c:
        b.data = src
    dst = b.data
    # alias: arg0.data, arg1
    alias_sink(dst)


def positive_copy_box_to_nested(n, b):
    n.box = b
    dst = n.box
    # alias: arg0.box, arg1
    alias_sink(dst)


def positive_nested_box_then_data(n, b, src):
    n.box = b
    b.data = src
    dst = n.box.data
    # alias: arg0.box.data, arg1.data, arg2
    alias_sink(dst)


def positive_pair_both_args(p, va, vb):
    p.a = va
    p.b = vb
    da = p.a
    # alias: arg0.a, arg1
    alias_sink(da)
    db = p.b
    # alias: arg0.b, arg2
    alias_sink(db)


def negative_pair_a_not_b(p):
    dst = p.a
    # alias: arg0.a, !arg0.b
    alias_sink(dst)


def negative_two_boxes(a, b):
    dst = a.data
    # alias: arg0.data, !arg1.data
    alias_sink(dst)


def negative_different_arg_fields(a, b, src):
    a.data = src
    dst = a.data
    # alias: arg0.data, arg2, !arg1.data
    alias_sink(dst)


def negative_store_does_not_alias_obj(b, src):
    b.data = src
    dst = b
    # alias: arg0, !arg1
    alias_sink(dst)


def positive_field_copy_a_to_b(p, src):
    p.a = src
    p.b = p.a
    dst = p.b
    # alias: arg0.a, arg1, ?arg0.b
    alias_sink(dst)


def positive_swap_pair_fields(p, va, vb):
    p.a = va
    p.b = vb
    p.a = p.b
    dst = p.a
    # alias: arg0.a, arg1, arg2, ?arg0.b
    alias_sink(dst)
