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

	@app.route('/benchmark/redirect-00/BenchmarkTest00814', methods=['GET'])
	def BenchmarkTest00814_get():
		return BenchmarkTest00814_post()

	@app.route('/benchmark/redirect-00/BenchmarkTest00814', methods=['POST'])
	def BenchmarkTest00814_post():
		RESPONSE = ""

		values = request.args.getlist("BenchmarkTest00814")
		param = ""
		if values:
			param = values[0]

		string23114 = 'help'
		string23114 += param
		string23114 += 'snapes on a plane'
		bar = string23114[4:-17]

		import flask

		return flask.redirect(bar)

		return RESPONSE

