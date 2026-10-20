import flask

class request_wrapper:
	request: flask.Request

	def __init__(self, request: flask.Request):
		self.request = request

	def get_form_parameter(self, name: str):
		return self.request.form.get(name)
	
	def get_query_parameter(self, name: str):
		return self.request.args.get(name)

	def get_cookie(self, name: str):
		return self.request.cookies.get(name)

	def get_safe_value(self, name: str):
		return "bar"
