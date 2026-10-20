'''
OWASP Benchmark for Python v0.1

This file is part of the Open Web Application Security Project (OWASP) Benchmark Project.
For details, please see https://owasp.org/www-project-benchmark.

The OWASP Benchmark is free software: you can redistribute it and/or modify it under the terms
of the GNU General Public License as published by the Free Software Foundation, version 3.

The OWASP Benchmark is distributed in the hope that it will be useful, but WITHOUT ANY
WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR
PURPOSE. See the GNU General Public License for more details.

  Author: Theo Cartsonis
  Created: 2025
'''

from flask import redirect, url_for, request, make_response, render_template
from helpers.utils import escape_for_html

def init(app):

	@app.route('/benchmark/trustbound-00/BenchmarkTest00260', methods=['GET'])
	def BenchmarkTest00260_get():
		return BenchmarkTest00260_post()

	@app.route('/benchmark/trustbound-00/BenchmarkTest00260', methods=['POST'])
	def BenchmarkTest00260_post():
		RESPONSE = ""

		values = request.form.getlist("BenchmarkTest00260")
		param = ""
		if values:
			param = values[0]

		superstring = f'39218{param}abcd'
		bar = superstring[len('39218'):len(superstring)-5]

		import flask

		flask.session['userid'] = bar

		RESPONSE += (
			f'Item: \'userid\' with value \'{escape_for_html(bar)}'
			'\'saved in session.'
		)

		return RESPONSE

