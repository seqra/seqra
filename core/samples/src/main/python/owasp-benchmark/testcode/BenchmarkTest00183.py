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

	@app.route('/benchmark/pathtraver-00/BenchmarkTest00183', methods=['GET'])
	def BenchmarkTest00183_get():
		return BenchmarkTest00183_post()

	@app.route('/benchmark/pathtraver-00/BenchmarkTest00183', methods=['POST'])
	def BenchmarkTest00183_post():
		RESPONSE = ""

		values = request.form.getlist("BenchmarkTest00183")
		param = ""
		if values:
			param = values[0]

		map40535 = {}
		map40535['keyA-40535'] = 'a-Value'
		map40535['keyB-40535'] = param
		map40535['keyC'] = 'another-Value'
		bar = "safe!"
		bar = map40535['keyB-40535']
		bar = map40535['keyA-40535']

		import helpers.utils

		fileName = None
		fd = None

		try:
			fileName = f'{helpers.utils.TESTFILES_DIR}/{bar}'
			with open(fileName, 'rb') as fd:
				RESPONSE += (
					f'The beginning of file: \'{escape_for_html(fileName)}\' is:\n\n'
					f'{escape_for_html(fd.read(1000).decode('utf-8'))}'
				)
		except IOError as e:
			RESPONSE += (
				f'Problem reading from file \'{{escape_for_html(fileName)}}\': '
				f'{escape_for_html(e.strerror)}'
			)

		return RESPONSE

