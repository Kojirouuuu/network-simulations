$latex = 'uplatex -synctex=1 -interaction=nonstopmode %O %S';
$bibtex = 'biber %O %B';
$dvipdf = 'dvipdfmx %O -o %D %S';
$pdf_mode = 3; # dvi -> pdf via dvipdfmx
$silent = 1;

# Useful defaults for Japanese uplatex + biblatex + dvipdfmx
$aux_dir = '.';
$out_dir = '.';
