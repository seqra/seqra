from helpers import alias_sink


def positive_list_read_elem(s):
    dst = s[0]
    # alias: arg0[]
    alias_sink(dst)


def positive_list_write_read(s, src):
    s[0] = src
    dst = s[0]
    # alias: arg0[], arg1
    alias_sink(dst)


def positive_list_write_read_other_index(s, src):
    s[0] = src
    dst = s[5]
    # alias: arg0[], arg1
    alias_sink(dst)


def positive_dict_read(m):
    dst = m["k"]
    # alias: arg0[]
    alias_sink(dst)


def positive_dict_write_read_diff_key(m, src):
    m["a"] = src
    dst = m["b"]
    # alias: arg0[], arg1
    alias_sink(dst)


def positive_var_index(s, i):
    dst = s[i]
    # alias: arg0[], !arg1
    alias_sink(dst)


def positive_list_of_box_read(s):
    dst = s[0].data
    # alias: arg0[].data
    alias_sink(dst)


def positive_list_of_box_write(s, src):
    s[0].data = src
    dst = s[0].data
    # alias: arg0[].data, arg1
    alias_sink(dst)


def positive_field_elem(b):
    dst = b.data[0]
    # alias: arg0.data[]
    alias_sink(dst)


def positive_weak_update_keeps_old(s, a, b):
    s[0] = a
    s[0] = b
    dst = s[0]
    # alias: arg1, arg2
    alias_sink(dst)
