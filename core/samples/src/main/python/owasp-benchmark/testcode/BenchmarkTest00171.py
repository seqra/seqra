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

	@app.route('/benchmark/pathtraver-00/BenchmarkTest00171', methods=['GET'])
	def BenchmarkTest00171_get():
		return BenchmarkTest00171_post()

	@app.route('/benchmark/pathtraver-00/BenchmarkTest00171', methods=['POST'])
	def BenchmarkTest00171_post():
		RESPONSE = ""

		values = request.form.getlist("BenchmarkTest00171")
		param = ""
		if values:
			param = values[0]

		map13848 = {}
		map13848['keyA-13848'] = 'a-Value'
		map13848['keyB-13848'] = param
		map13848['keyC'] = 'another-Value'
		bar = map13848['keyB-13848']

		import codecs
		import helpers.utils

		try:
			fileTarget = codecs.open(f'{helpers.utils.TESTFILES_DIR}/{bar}','r','utf-8')

			RESPONSE += (
				f"Access to file: \'{escape_for_html(fileTarget.name)}\' created."
			)

			RESPONSE += (
				" And file already exists."
			)

		except FileNotFoundError:
			RESPONSE += (
				" But file doesn't exist yet."
			)

		return RESPONSE

