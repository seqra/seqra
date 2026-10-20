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

	@app.route('/benchmark/pathtraver-02/BenchmarkTest01192', methods=['GET'])
	def BenchmarkTest01192_get():
		return BenchmarkTest01192_post()

	@app.route('/benchmark/pathtraver-02/BenchmarkTest01192', methods=['POST'])
	def BenchmarkTest01192_post():
		RESPONSE = ""

		param = ""
		for name in request.form.keys():
			if "BenchmarkTest01192" in request.form.getlist(name):
				param = name
				break


		import pathlib
		import helpers.utils

		try:
			testfiles = pathlib.Path(helpers.utils.TESTFILES_DIR)
			p = testfiles / param
			RESPONSE += (
				f'The beginning of file: \'{escape_for_html(str(p))}\' is:\n\n'
				f'{escape_for_html(p.read_text()[:1000])}'
			)
		except OSError:
			RESPONSE += (
				f'Problem reading from file \'{{escape_for_html(fileName)}}\': '
				f'{escape_for_html(e.strerror)}'
			)

		return RESPONSE


