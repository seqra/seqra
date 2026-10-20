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

	@app.route('/benchmark/xss-00/BenchmarkTest00188', methods=['GET'])
	def BenchmarkTest00188_get():
		return BenchmarkTest00188_post()

	@app.route('/benchmark/xss-00/BenchmarkTest00188', methods=['POST'])
	def BenchmarkTest00188_post():
		RESPONSE = ""

		values = request.form.getlist("BenchmarkTest00188")
		param = ""
		if values:
			param = values[0]

		superstring = f'20259{param}abcd'
		bar = superstring[len('20259'):len(superstring)-5]


		dict = {}
		dict['bar'] = bar
		dict['otherarg'] = 'this is it'
		RESPONSE += (
			'bar is \'{0[bar]}\' and otherarg is \'{0[otherarg]}\''.format(dict)
		)

		return RESPONSE

