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

	@app.route('/benchmark/xss-00/BenchmarkTest00149', methods=['GET'])
	def BenchmarkTest00149_get():
		return BenchmarkTest00149_post()

	@app.route('/benchmark/xss-00/BenchmarkTest00149', methods=['POST'])
	def BenchmarkTest00149_post():
		RESPONSE = ""

		param = request.form.get("BenchmarkTest00149")
		if not param:
			param = ""

		import markupsafe
		
		bar = markupsafe.escape(param)


		RESPONSE += (
			'The value of the bar parameter is now in a custom header.'
		)

		RESPONSE = make_response((RESPONSE, {'yourBenchmarkTest00149': bar}))
		

		return RESPONSE

