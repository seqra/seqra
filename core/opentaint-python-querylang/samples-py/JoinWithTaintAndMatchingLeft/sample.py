def create_initial_data() -> str:
    return "data"


def get_untrusted_input() -> str:
    return "untrusted"


def process_input(x: str) -> str:
    return x


def transform_data(x: str) -> str:
    return x


def execute_dangerous(target: str) -> None:
    pass


def Positive_matching_flow():
    data = create_initial_data()
    execute_dangerous(data)


def Positive_taint_flow():
    untrusted = get_untrusted_input()
    processed = process_input(untrusted)
    result = transform_data(processed)
    execute_dangerous(result)


def Negative_missing_untrusted_label():
    safe = "safe"
    processed = process_input(safe)
    result = transform_data(processed)
    execute_dangerous(result)


def Negative_missing_processed_label():
    untrusted = get_untrusted_input()
    result = transform_data(untrusted)
    execute_dangerous(result)


def Negative_no_sink():
    data = create_initial_data()
    print(data)
