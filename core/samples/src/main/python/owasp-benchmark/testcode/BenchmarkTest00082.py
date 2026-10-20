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

	@app.route('/benchmark/xss-00/BenchmarkTest00082', methods=['GET'])
	def BenchmarkTest00082_get():
		return BenchmarkTest00082_post()

	@app.route('/benchmark/xss-00/BenchmarkTest00082', methods=['POST'])
	def BenchmarkTest00082_post():
		RESPONSE = ""

		param = request.form.get("BenchmarkTest00082")
		if not param:
			param = ""

		map15517 = {}
		map15517['keyA-15517'] = 'a-Value'
		map15517['keyB-15517'] = param
		map15517['keyC'] = 'another-Value'
		bar = "safe!"
		bar = map15517['keyB-15517']
		bar = map15517['keyA-15517']


		RESPONSE += (
			f'Parameter value: {bar}'
		)

		return RESPONSE

